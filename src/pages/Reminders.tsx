import { useState, useEffect, useMemo } from "react";
import { storage } from "@/lib/storage";
import { notificationService } from "@/lib/notifications";
import { Reminder, PRIORITY_LABELS, PRIORITY_COLORS } from "@/types/reminder";
import { Contact } from "@/types/contact";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import {
  Bell,
  Plus,
  Check,
  Clock,
  User,
  Phone,
  PhoneOff,
  Calendar,
  Trash2,
  AlertCircle,
} from "lucide-react";
import { Link } from "react-router-dom";
import { format, isToday, isTomorrow, isPast, differenceInDays } from "date-fns";
import { ru } from "date-fns/locale";
import { ReminderDialog } from "@/components/ReminderDialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useToast } from "@/hooks/use-toast";

interface ReminderWithContact extends Reminder {
  contact?: Contact;
}

type SortOption = "date" | "priority" | "contact";
type FilterOption = "all" | "overdue" | "today" | "upcoming" | "no-date" | "completed";

const Reminders = () => {
  const { toast } = useToast();
  const [reminders, setReminders] = useState<ReminderWithContact[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [activeTab, setActiveTab] = useState("reminders");
  const [sortBy, setSortBy] = useState<SortOption>("date");
  const [filterBy, setFilterBy] = useState<FilterOption>("all");
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editingReminder, setEditingReminder] = useState<Reminder | null>(null);
  const [deletingReminder, setDeletingReminder] = useState<Reminder | null>(null);

  useEffect(() => {
    loadData();
    const cleanupPromise = notificationService.setupListeners();
    void notificationService.reconcileNotifications();
    return () => {
      void cleanupPromise.then((cleanup) => cleanup());
    };
  }, []);

  const loadData = () => {
    const loadedContacts = storage.getContacts();
    const loadedReminders = storage.getReminders();
    setContacts(loadedContacts);

    const contactMap = new Map(loadedContacts.map((c) => [c.id, c]));
    const remindersWithContacts: ReminderWithContact[] = loadedReminders.map((r) => ({
      ...r,
      contact: r.contactId ? contactMap.get(r.contactId) : undefined,
    }));
    setReminders(remindersWithContacts);
  };

  const handleAddReminder = async (data: {
    text: string;
    dueAt: string | null;
    priority: 0 | 1 | 2;
    contactId: string | null;
  }) => {
    const now = new Date().toISOString();
    const newReminder: Reminder = {
      id: crypto.randomUUID(),
      contactId: data.contactId,
      text: data.text,
      dueAt: data.dueAt,
      priority: data.priority,
      isDone: false,
      completedAt: null,
      createdAt: now,
      updatedAt: now,
      notificationId: null,
    };

    // Schedule notification if there's a due date
    if (data.dueAt) {
      const contact = data.contactId
        ? contacts.find((c) => c.id === data.contactId)
        : undefined;
      const notificationId = await notificationService.scheduleReminder(
        newReminder,
        contact?.name
      );
      newReminder.notificationId = notificationId;
    }

    if (!storage.addReminder(newReminder)) {
      await notificationService.cancelReminderNotification(newReminder);
      toast({
        title: "Ошибка сохранения",
        description: "Не удалось сохранить напоминание",
        variant: "destructive",
      });
      return;
    }
    loadData();
    setShowAddDialog(false);
    toast({
      title: "Напоминание создано",
      description: data.dueAt
        ? `Уведомление запланировано на ${format(new Date(data.dueAt), "d MMMM yyyy, HH:mm", { locale: ru })}`
        : "Напоминание без срока",
    });
  };

  const handleEditReminder = async (data: {
    text: string;
    dueAt: string | null;
    priority: 0 | 1 | 2;
    contactId: string | null;
  }) => {
    if (!editingReminder) return;

    const updatedReminder: Reminder = {
      ...editingReminder,
      ...data,
      updatedAt: new Date().toISOString(),
    };

    // Reschedule notification
    const contact = data.contactId
      ? contacts.find((c) => c.id === data.contactId)
      : undefined;
    const notificationId = await notificationService.rescheduleReminder(
      updatedReminder,
      contact?.name
    );
    updatedReminder.notificationId = notificationId;

    if (!storage.updateReminder(editingReminder.id, updatedReminder)) {
      if (notificationId !== null) {
        await notificationService.cancelNotification(notificationId);
      }
      toast({
        title: "Ошибка сохранения",
        description: "Не удалось сохранить напоминание",
        variant: "destructive",
      });
      return;
    }
    loadData();
    setEditingReminder(null);
    toast({
      title: "Напоминание обновлено",
    });
  };

  const handleToggleDone = async (reminder: ReminderWithContact) => {
    const updated = storage.toggleReminderDone(reminder.id);
    if (updated) {
      // Cancel notification if marking as done
      if (updated.isDone && reminder.notificationId !== null) {
        await notificationService.cancelNotification(reminder.notificationId);
      } else if (!updated.isDone && updated.dueAt) {
        // Reschedule notification if marking as not done
        const notificationId = await notificationService.scheduleReminder(
          updated,
          reminder.contact?.name
        );
        if (notificationId !== null) {
          storage.updateReminder(updated.id, {
            ...updated,
            notificationId,
          });
        }
      }
      loadData();
    }
  };

  const handleDeleteReminder = async () => {
    if (!deletingReminder) return;

    // Cancel notification if exists
    if (deletingReminder.notificationId !== null) {
      await notificationService.cancelNotification(deletingReminder.notificationId);
    }

    storage.deleteReminder(deletingReminder.id);
    loadData();
    setDeletingReminder(null);
    toast({
      title: "Напоминание удалено",
    });
  };

  const filteredAndSortedReminders = useMemo(() => {
    let filtered = [...reminders];

    // Apply filter
    switch (filterBy) {
      case "overdue":
        filtered = filtered.filter(
          (r) => r.dueAt && isPast(new Date(r.dueAt)) && !r.isDone
        );
        break;
      case "today":
        filtered = filtered.filter((r) => r.dueAt && isToday(new Date(r.dueAt)));
        break;
      case "upcoming":
        filtered = filtered.filter(
          (r) => r.dueAt && !isPast(new Date(r.dueAt)) && !isToday(new Date(r.dueAt))
        );
        break;
      case "no-date":
        filtered = filtered.filter((r) => !r.dueAt);
        break;
      case "completed":
        filtered = filtered.filter((r) => r.isDone);
        break;
      case "all":
      default:
        filtered = filtered.filter((r) => !r.isDone);
        break;
    }

    // Apply sort
    switch (sortBy) {
      case "date":
        filtered.sort((a, b) => {
          if (!a.dueAt && !b.dueAt) return 0;
          if (!a.dueAt) return 1;
          if (!b.dueAt) return -1;
          return new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime();
        });
        break;
      case "priority":
        filtered.sort((a, b) => b.priority - a.priority);
        break;
      case "contact":
        filtered.sort((a, b) => {
          const nameA = a.contact?.name || "";
          const nameB = b.contact?.name || "";
          return nameA.localeCompare(nameB, "ru");
        });
        break;
    }

    return filtered;
  }, [reminders, filterBy, sortBy]);

  // Recent calls - contacts called in the last 30 days
  const recentCalls = useMemo(() => {
    return contacts
      .filter((c) => c.phone)
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 20);
  }, [contacts]);

  // Not called - contacts with phone but not updated recently
  const notCalled = useMemo(() => {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    return contacts
      .filter(
        (c) => c.phone && new Date(c.updatedAt) < thirtyDaysAgo
      )
      .sort((a, b) => new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime());
  }, [contacts]);

  const formatDueDate = (dueAt: string | null): string => {
    if (!dueAt) return "Без срока";
    const date = new Date(dueAt);
    if (isToday(date)) return `Сегодня, ${format(date, "HH:mm")}`;
    if (isTomorrow(date)) return `Завтра, ${format(date, "HH:mm")}`;
    if (isPast(date)) {
      const days = Math.abs(differenceInDays(date, new Date()));
      return `Просрочено на ${days} дн.`;
    }
    return format(date, "d MMM, HH:mm", { locale: ru });
  };

  const getDueBadgeVariant = (
    dueAt: string | null,
    isDone: boolean
  ): "default" | "destructive" | "secondary" | "outline" => {
    if (isDone) return "secondary";
    if (!dueAt) return "outline";
    const date = new Date(dueAt);
    if (isPast(date)) return "destructive";
    if (isToday(date)) return "default";
    return "secondary";
  };

  const renderReminderCard = (reminder: ReminderWithContact) => (
    <Card
      key={reminder.id}
      className={`transition-all duration-300 ${
        reminder.isDone ? "opacity-60" : "hover:shadow-lg hover:scale-[1.01]"
      }`}
    >
      <CardContent className="p-4">
        <div className="flex items-start gap-3">
          <Button
            variant="ghost"
            size="icon"
            className={`flex-shrink-0 rounded-full ${
              reminder.isDone
                ? "bg-green-500/20 text-green-500"
                : "bg-muted hover:bg-muted/80"
            }`}
            onClick={() => handleToggleDone(reminder)}
          >
            {reminder.isDone ? (
              <Check className="w-4 h-4" />
            ) : (
              <Clock className="w-4 h-4" />
            )}
          </Button>

          <div className="flex-1 min-w-0">
            <p
              className={`font-medium mb-1 ${
                reminder.isDone ? "line-through text-muted-foreground" : ""
              }`}
            >
              {reminder.text}
            </p>

            <div className="flex flex-wrap gap-2 items-center">
              <Badge variant={getDueBadgeVariant(reminder.dueAt, reminder.isDone)}>
                {reminder.isDone ? "Выполнено" : formatDueDate(reminder.dueAt)}
              </Badge>

              <Badge className={`${PRIORITY_COLORS[reminder.priority]} text-white`}>
                {PRIORITY_LABELS[reminder.priority]}
              </Badge>

              {reminder.contact && (
                <Link to={`/contact/${reminder.contact.id}`}>
                  <Badge variant="outline" className="cursor-pointer hover:bg-muted">
                    <User className="w-3 h-3 mr-1" />
                    {reminder.contact.name}
                  </Badge>
                </Link>
              )}
            </div>
          </div>

          <div className="flex gap-1 flex-shrink-0">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setEditingReminder(reminder)}
            >
              <Calendar className="w-4 h-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setDeletingReminder(reminder)}
            >
              <Trash2 className="w-4 h-4 text-destructive" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );

  const renderContactCard = (contact: Contact, showPhone: boolean = true) => (
    <Link key={contact.id} to={`/contact/${contact.id}`}>
      <Card className="hover:shadow-lg transition-all duration-300 hover:scale-[1.01] cursor-pointer">
        <CardContent className="p-4">
          <div className="flex items-center gap-3">
            <Avatar className="w-12 h-12 flex-shrink-0">
              {contact.avatar ? (
                <AvatarImage src={contact.avatar} alt={contact.name} />
              ) : (
                <AvatarFallback>
                  <User className="w-6 h-6" />
                </AvatarFallback>
              )}
            </Avatar>
            <div className="flex-1 min-w-0">
              <h3 className="font-semibold truncate">{contact.name}</h3>
              {showPhone && contact.phone && (
                <p className="text-sm text-muted-foreground flex items-center gap-1">
                  <Phone className="w-3 h-3" />
                  {contact.phone}
                </p>
              )}
              <p className="text-xs text-muted-foreground">
                Обновлен: {format(new Date(contact.updatedAt), "d MMM yyyy", { locale: ru })}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </Link>
  );

  return (
    <div className="min-h-screen bg-background pb-20 p-4 md:p-6 lg:p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <header className="flex items-center justify-between">
          <div className="space-y-1">
            <h1 className="text-3xl font-bold flex items-center gap-2">
              <Bell className="w-8 h-8" />
              Напоминания
            </h1>
            <p className="text-muted-foreground">
              Управляйте напоминаниями и звонками
            </p>
          </div>
          <Button onClick={() => setShowAddDialog(true)}>
            <Plus className="w-4 h-4 mr-2" />
            Добавить
          </Button>
        </header>

        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="grid w-full grid-cols-3">
            <TabsTrigger value="reminders" className="flex items-center gap-1">
              <Bell className="w-4 h-4" />
              <span className="hidden sm:inline">Напоминания</span>
            </TabsTrigger>
            <TabsTrigger value="recentCalls" className="flex items-center gap-1">
              <Phone className="w-4 h-4" />
              <span className="hidden sm:inline">Недавние</span>
            </TabsTrigger>
            <TabsTrigger value="notCalled" className="flex items-center gap-1">
              <PhoneOff className="w-4 h-4" />
              <span className="hidden sm:inline">Не звонили</span>
            </TabsTrigger>
          </TabsList>

          <TabsContent value="reminders" className="space-y-4">
            {/* Filters and Sort */}
            <div className="flex flex-wrap gap-2">
              <select
                value={filterBy}
                onChange={(e) => setFilterBy(e.target.value as FilterOption)}
                className="px-3 py-2 rounded-md border bg-background text-sm"
              >
                <option value="all">Все активные</option>
                <option value="overdue">Просроченные</option>
                <option value="today">Сегодня</option>
                <option value="upcoming">Предстоящие</option>
                <option value="no-date">Без срока</option>
                <option value="completed">Выполненные</option>
              </select>

              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortOption)}
                className="px-3 py-2 rounded-md border bg-background text-sm"
              >
                <option value="date">По дате</option>
                <option value="priority">По приоритету</option>
                <option value="contact">По контакту</option>
              </select>
            </div>

            {/* Reminders List */}
            <div className="space-y-3">
              {filteredAndSortedReminders.length === 0 ? (
                <div className="text-center py-12">
                  <Bell className="w-16 h-16 mx-auto mb-4 text-muted-foreground" />
                  <p className="text-muted-foreground text-lg">
                    {filterBy === "all"
                      ? "Нет активных напоминаний"
                      : "Нет напоминаний по выбранному фильтру"}
                  </p>
                  <Button
                    variant="outline"
                    className="mt-4"
                    onClick={() => setShowAddDialog(true)}
                  >
                    <Plus className="w-4 h-4 mr-2" />
                    Создать напоминание
                  </Button>
                </div>
              ) : (
                filteredAndSortedReminders.map(renderReminderCard)
              )}
            </div>
          </TabsContent>

          <TabsContent value="recentCalls" className="space-y-3">
            {recentCalls.length === 0 ? (
              <div className="text-center py-12">
                <Phone className="w-16 h-16 mx-auto mb-4 text-muted-foreground" />
                <p className="text-muted-foreground text-lg">
                  Нет недавних контактов
                </p>
              </div>
            ) : (
              recentCalls.map((contact) => renderContactCard(contact))
            )}
          </TabsContent>

          <TabsContent value="notCalled" className="space-y-3">
            {notCalled.length === 0 ? (
              <div className="text-center py-12">
                <PhoneOff className="w-16 h-16 mx-auto mb-4 text-muted-foreground" />
                <p className="text-muted-foreground text-lg">
                  Все контакты актуальны
                </p>
                <p className="text-muted-foreground text-sm mt-2">
                  Здесь появятся контакты, которые не обновлялись более 30 дней
                </p>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-2 p-3 bg-yellow-500/10 rounded-lg">
                  <AlertCircle className="w-5 h-5 text-yellow-500" />
                  <p className="text-sm">
                    {notCalled.length} контакт(ов) не обновлялись более 30 дней
                  </p>
                </div>
                {notCalled.map((contact) => renderContactCard(contact))}
              </>
            )}
          </TabsContent>
        </Tabs>
      </div>

      {/* Add/Edit Reminder Dialog */}
      <ReminderDialog
        open={showAddDialog || !!editingReminder}
        onOpenChange={(open) => {
          if (!open) {
            setShowAddDialog(false);
            setEditingReminder(null);
          }
        }}
        onSubmit={editingReminder ? handleEditReminder : handleAddReminder}
        contacts={contacts}
        initialData={
          editingReminder
            ? {
                text: editingReminder.text,
                dueAt: editingReminder.dueAt,
                priority: editingReminder.priority,
                contactId: editingReminder.contactId,
              }
            : undefined
        }
        title={editingReminder ? "Редактировать напоминание" : "Новое напоминание"}
      />

      {/* Delete Confirmation Dialog */}
      <AlertDialog
        open={!!deletingReminder}
        onOpenChange={(open) => !open && setDeletingReminder(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Удалить напоминание?</AlertDialogTitle>
            <AlertDialogDescription>
              Это действие нельзя отменить. Напоминание будет полностью удалено.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteReminder}>
              Удалить
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default Reminders;
