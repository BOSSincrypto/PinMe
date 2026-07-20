import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { isSafeExternalUrl, storage } from "@/lib/storage";
import { notificationService } from "@/lib/notifications";
import { Contact, ContactFormData } from "@/types/contact";
import { Reminder, PRIORITY_LABELS, PRIORITY_COLORS } from "@/types/reminder";
import { ContactForm } from "@/components/ContactForm";
import { PasswordDialog } from "@/components/PasswordDialog";
import { ReminderDialog } from "@/components/ReminderDialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { ArrowLeft, Edit, Trash2, User, Phone, Mail, Lock, Briefcase, Calendar, Cake, Bell, Plus, Check, Clock } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { format, isPast, isToday, isTomorrow } from "date-fns";
import { ru } from "date-fns/locale";
import { createPasswordDigest, verifyPasswordDigest } from "@/lib/encryption";
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

const ContactDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [contact, setContact] = useState<Contact | null>(null);
  const [reminders, setReminders] = useState<Reminder[]>([]);
  const [allContacts, setAllContacts] = useState<Contact[]>([]);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [showPasswordDialog, setShowPasswordDialog] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [showReminderDialog, setShowReminderDialog] = useState(false);
  const [editingReminder, setEditingReminder] = useState<Reminder | null>(null);
  const [deletingReminder, setDeletingReminder] = useState<Reminder | null>(null);

  useEffect(() => {
    if (id) {
      const foundContact = storage.getContact(id);
      if (foundContact) {
        setContact(foundContact);
        loadReminders(id);
        setAllContacts(storage.getContacts());
      } else {
        navigate("/");
      }
    }
  }, [id, navigate]);

  const loadReminders = (contactId: string) => {
    const contactReminders = storage.getRemindersByContact(contactId);
    setReminders(contactReminders);
  };

  const handlePasswordSubmit = async (password: string) => {
    if (!contact || !id) {
      return;
    }
    const verified = contact.passwordHash && contact.passwordSalt
      ? await verifyPasswordDigest(
          password,
          contact.passwordSalt,
          contact.passwordHash,
          contact.passwordIterations || 100000,
        )
      : password === contact.password;
    if (verified) {
      if (!contact.passwordHash || !contact.passwordSalt || contact.password) {
        const credentials = await createPasswordDigest(password);
        const migratedContact: Contact = {
          ...contact,
          passwordHash: credentials.hash,
          passwordSalt: credentials.salt,
          passwordIterations: credentials.iterations,
          password: undefined,
          updatedAt: new Date().toISOString(),
        };
        if (storage.updateContact(id, migratedContact)) {
          setContact(migratedContact);
        }
      }
      setIsAuthenticated(true);
      setShowPasswordDialog(false);
    } else {
      toast({
        title: "Неверный пароль",
        description: "Попробуйте еще раз",
        variant: "destructive",
      });
    }
  };

  const handleUpdate = async (data: ContactFormData) => {
    if (contact && id) {
      const { password, ...contactData } = data;
      const hasStoredDigest = Boolean(contact.passwordHash && contact.passwordSalt);
      if (!password && !hasStoredDigest && !contact.password) {
        toast({
          title: "Ошибка сохранения",
          description: "У контакта отсутствует пароль доступа",
          variant: "destructive",
        });
        return;
      }
      const credentials = password
        ? await createPasswordDigest(password)
        : contact.passwordHash && contact.passwordSalt
          ? {
              hash: contact.passwordHash,
              salt: contact.passwordSalt,
              iterations: contact.passwordIterations,
            }
          : await createPasswordDigest(contact.password || "");
      const updatedContact: Contact = {
        ...contact,
        ...contactData,
        passwordHash: credentials.hash,
        passwordSalt: credentials.salt,
        passwordIterations: credentials.iterations,
        password: undefined,
        updatedAt: new Date().toISOString(),
      };
      if (!storage.updateContact(id, updatedContact)) {
        toast({
          title: "Ошибка сохранения",
          description: "Не удалось сохранить изменения. Освободите место и попробуйте снова",
          variant: "destructive",
        });
        return;
      }
      setContact(updatedContact);
      setIsEditing(false);
      toast({
        title: "Контакт обновлен",
        description: "Изменения сохранены",
      });
    }
  };

  const handleDelete = async () => {
    if (id) {
      const relatedReminders = storage.getRemindersByContact(id);
      if (!storage.deleteContact(id)) {
        toast({
          title: "Ошибка удаления",
          description: "Не удалось удалить контакт",
          variant: "destructive",
        });
        return;
      }
      await Promise.all(
        relatedReminders.map((reminder) => notificationService.cancelReminderNotification(reminder)),
      );
      toast({
        title: "Контакт удален",
        description: "Контакт удален из списка",
      });
      navigate("/");
    }
  };

  const handleAddReminder = async (data: {
    text: string;
    dueAt: string | null;
    priority: 0 | 1 | 2;
    contactId: string | null;
  }) => {
    if (!id) return;
    const now = new Date().toISOString();
    const newReminder: Reminder = {
      id: crypto.randomUUID(),
      contactId: id,
      text: data.text,
      dueAt: data.dueAt,
      priority: data.priority,
      isDone: false,
      completedAt: null,
      createdAt: now,
      updatedAt: now,
      notificationId: null,
    };

    if (data.dueAt) {
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
    loadReminders(id);
    setShowReminderDialog(false);
    toast({
      title: "Напоминание создано",
    });
  };

  const handleEditReminder = async (data: {
    text: string;
    dueAt: string | null;
    priority: 0 | 1 | 2;
    contactId: string | null;
  }) => {
    if (!editingReminder || !id) return;

    const updatedReminder: Reminder = {
      ...editingReminder,
      ...data,
      contactId: id,
      updatedAt: new Date().toISOString(),
    };

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
    loadReminders(id);
    setEditingReminder(null);
    toast({
      title: "Напоминание обновлено",
    });
  };

  const handleToggleReminderDone = async (reminder: Reminder) => {
    if (!id) return;
    const updated = storage.toggleReminderDone(reminder.id);
    if (updated) {
      if (updated.isDone && reminder.notificationId !== null) {
        await notificationService.cancelNotification(reminder.notificationId);
      } else if (!updated.isDone && updated.dueAt) {
        const notificationId = await notificationService.scheduleReminder(
          updated,
          contact?.name
        );
        if (notificationId !== null) {
          storage.updateReminder(updated.id, {
            ...updated,
            notificationId,
          });
        }
      }
      loadReminders(id);
    }
  };

  const handleDeleteReminder = async () => {
    if (!deletingReminder || !id) return;

    if (deletingReminder.notificationId !== null) {
      await notificationService.cancelNotification(deletingReminder.notificationId);
    }

    storage.deleteReminder(deletingReminder.id);
    loadReminders(id);
    setDeletingReminder(null);
    toast({
      title: "Напоминание удалено",
    });
  };

  const formatDueDate = (dueAt: string | null): string => {
    if (!dueAt) return "Без срока";
    const date = new Date(dueAt);
    if (isToday(date)) return `Сегодня, ${format(date, "HH:mm")}`;
    if (isTomorrow(date)) return `Завтра, ${format(date, "HH:mm")}`;
    if (isPast(date)) return "Просрочено";
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

  if (!contact) {
    return null;
  }

  if (!isAuthenticated) {
    return (
      <PasswordDialog
        open={showPasswordDialog}
        onOpenChange={(open) => {
          if (!open) {
            navigate("/");
          }
          setShowPasswordDialog(open);
        }}
        onSubmit={handlePasswordSubmit}
        contactName={contact.name}
      />
    );
  }

  if (isEditing) {
    return (
      <div className="min-h-screen bg-background p-4 md:p-6 lg:p-8">
        <div className="max-w-2xl mx-auto space-y-6">
          <div className="flex items-center gap-4">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsEditing(false)}
            >
              <ArrowLeft className="w-5 h-5" />
            </Button>
            <h1 className="text-3xl font-bold">Редактировать контакт</h1>
          </div>

          <ContactForm
            initialData={{
              name: contact.name,
              phone: contact.phone,
              email: contact.email,
              workplace: contact.workplace,
              position: contact.position,
              source: contact.source,
              password: "",
              notes: contact.notes,
              avatar: contact.avatar,
              tags: contact.tags,
              birthday: contact.birthday,
              socialMedia: contact.socialMedia,
              events: contact.events,
              additionalInfo: contact.additionalInfo,
            }}
            onSubmit={handleUpdate}
            onCancel={() => setIsEditing(false)}
            submitLabel="Сохранить изменения"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background pb-32 p-4 md:p-6 lg:p-8">
      <div className="max-w-2xl mx-auto space-y-4 sm:space-y-6">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 sm:gap-4 min-w-0">
            <Button variant="ghost" size="icon" onClick={() => navigate("/")} className="flex-shrink-0">
              <ArrowLeft className="w-4 h-4 sm:w-5 sm:h-5" />
            </Button>
            <h1 className="text-xl sm:text-3xl font-bold truncate">{contact.name}</h1>
          </div>
          <div className="flex gap-1.5 sm:gap-2 flex-shrink-0">
            <Button variant="outline" size="sm" onClick={() => setIsEditing(true)}>
              <Edit className="w-4 h-4 sm:mr-2" />
              <span className="hidden sm:inline">Изменить</span>
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={() => setShowDeleteDialog(true)}
            >
              <Trash2 className="w-4 h-4 sm:mr-2" />
              <span className="hidden sm:inline">Удалить</span>
            </Button>
          </div>
        </div>

        <Card>
          <CardContent className="pt-6">
            <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4 sm:gap-6 mb-6">
              <Avatar className="w-24 h-24 sm:w-32 sm:h-32 flex-shrink-0">
                {contact.avatar ? (
                  <AvatarImage src={contact.avatar} alt={contact.name} />
                ) : (
                  <AvatarFallback>
                    <User className="w-12 h-12 sm:w-16 sm:h-16" />
                  </AvatarFallback>
                )}
              </Avatar>
              <div className="flex-1 text-center sm:text-left">
                <h2 className="text-xl sm:text-2xl font-bold mb-3">{contact.name}</h2>
                {contact.tags && contact.tags.length > 0 && (
                  <div className="flex flex-wrap gap-2 justify-center sm:justify-start">
                    {contact.tags.map((tag) => (
                      <Badge
                        key={tag.id}
                        style={{ backgroundColor: tag.color }}
                        className="text-white px-3 py-1"
                      >
                        {tag.name}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
              <User className="w-4 h-4 sm:w-5 sm:h-5" />
              Основная информация
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {contact.workplace && (
              <div>
                <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                  <Briefcase className="w-4 h-4" />
                  Место работы
                </div>
                <p className="font-medium">{contact.workplace}</p>
              </div>
            )}
            {contact.position && (
              <div>
                <div className="text-muted-foreground text-sm mb-1">
                  Должность
                </div>
                <p className="font-medium">{contact.position}</p>
              </div>
            )}
            {contact.source && (
              <div>
                <div className="text-muted-foreground text-sm mb-1">
                  Откуда узнал
                </div>
                <p className="font-medium">{contact.source}</p>
              </div>
            )}
            {contact.phone && (
              <div>
                <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                  <Phone className="w-4 h-4" />
                  Телефон
                </div>
                <a
                  href={`tel:${contact.phone}`}
                  className="font-medium hover:text-primary transition-colors"
                >
                  {contact.phone}
                </a>
              </div>
            )}
            {contact.email && (
              <div>
                <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                  <Mail className="w-4 h-4" />
                  Email
                </div>
                <p className="font-medium">{contact.email}</p>
              </div>
            )}
            {contact.notes && (
              <div>
                <div className="text-muted-foreground text-sm mb-1">
                  Заметки
                </div>
                <p className="font-medium whitespace-pre-wrap">
                  {contact.notes}
                </p>
              </div>
            )}
          </CardContent>
        </Card>

        {contact.additionalInfo &&
          Object.keys(contact.additionalInfo).length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>Дополнительная информация</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {Object.entries(contact.additionalInfo).map(([key, value]) => (
                  <div key={key}>
                    <div className="text-muted-foreground text-sm mb-1">
                      {key}
                    </div>
                    <p className="font-medium">{value}</p>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}

        {(contact.birthday || (contact.socialMedia && contact.socialMedia.length > 0) || (contact.events && contact.events.length > 0)) && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Calendar className="w-5 h-5" />
                Даты и контакты
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {contact.birthday && (
                <div>
                  <div className="flex items-center gap-2 text-muted-foreground text-sm mb-1">
                    <Cake className="w-4 h-4" />
                    День рождения
                  </div>
                  <p className="font-medium">
                    {format(new Date(contact.birthday), "d MMMM yyyy", { locale: ru })}
                  </p>
                </div>
              )}

              {contact.socialMedia && contact.socialMedia.length > 0 && (
                <div>
                  <div className="text-muted-foreground text-sm mb-2">
                    Социальные сети
                  </div>
                  <div className="space-y-2">
                    {contact.socialMedia.map((social, index) => (
                      <div key={index}>
                        <span className="text-sm font-medium">{social.platform}: </span>
                        {isSafeExternalUrl(social.url) ? (
                          <a
                            href={social.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-sm text-primary hover:underline"
                          >
                            {social.url}
                          </a>
                        ) : (
                          <span className="text-sm">{social.url}</span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {contact.events && contact.events.length > 0 && (
                <div>
                  <div className="text-muted-foreground text-sm mb-2">
                    События
                  </div>
                  <div className="space-y-2">
                    {contact.events.map((event) => (
                      <div key={event.id} className="flex items-center justify-between">
                        <span className="font-medium">{event.title}</span>
                        <span className="text-sm text-muted-foreground">
                          {format(new Date(event.date), "d MMMM yyyy", { locale: ru })}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Bell className="w-5 h-5" />
                Напоминания
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowReminderDialog(true)}
              >
                <Plus className="w-4 h-4 mr-1" />
                Добавить
              </Button>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {reminders.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-4">
                Нет напоминаний для этого контакта
              </p>
            ) : (
              <div className="space-y-3">
                {reminders.map((reminder) => (
                  <div
                    key={reminder.id}
                    className={`flex items-start gap-3 p-3 rounded-lg border ${
                      reminder.isDone ? "opacity-60 bg-muted/30" : "bg-card"
                    }`}
                  >
                    <Button
                      variant="ghost"
                      size="icon"
                      className={`flex-shrink-0 rounded-full h-8 w-8 ${
                        reminder.isDone
                          ? "bg-green-500/20 text-green-500"
                          : "bg-muted hover:bg-muted/80"
                      }`}
                      onClick={() => handleToggleReminderDone(reminder)}
                    >
                      {reminder.isDone ? (
                        <Check className="w-4 h-4" />
                      ) : (
                        <Clock className="w-4 h-4" />
                      )}
                    </Button>

                    <div className="flex-1 min-w-0">
                      <p
                        className={`font-medium text-sm ${
                          reminder.isDone ? "line-through text-muted-foreground" : ""
                        }`}
                      >
                        {reminder.text}
                      </p>
                      <div className="flex flex-wrap gap-1.5 mt-1.5">
                        <Badge
                          variant={getDueBadgeVariant(reminder.dueAt, reminder.isDone)}
                          className="text-xs"
                        >
                          {reminder.isDone ? "Выполнено" : formatDueDate(reminder.dueAt)}
                        </Badge>
                        <Badge
                          className={`${PRIORITY_COLORS[reminder.priority]} text-white text-xs`}
                        >
                          {PRIORITY_LABELS[reminder.priority]}
                        </Badge>
                      </div>
                    </div>

                    <div className="flex gap-1 flex-shrink-0">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => setEditingReminder(reminder)}
                      >
                        <Edit className="w-3.5 h-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => setDeletingReminder(reminder)}
                      >
                        <Trash2 className="w-3.5 h-3.5 text-destructive" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Lock className="w-5 h-5" />
              Безопасность
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Этот контакт защищен паролем. Все данные хранятся локально на
              вашем устройстве.
            </p>
          </CardContent>
        </Card>

        <ReminderDialog
          open={showReminderDialog || !!editingReminder}
          onOpenChange={(open) => {
            if (!open) {
              setShowReminderDialog(false);
              setEditingReminder(null);
            }
          }}
          onSubmit={editingReminder ? handleEditReminder : handleAddReminder}
          contacts={allContacts}
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

        <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Удалить контакт?</AlertDialogTitle>
              <AlertDialogDescription>
                Это действие нельзя отменить. Контакт {contact.name} будет
                полностью удален.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Отмена</AlertDialogCancel>
              <AlertDialogAction onClick={handleDelete}>
                Удалить
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
    </div>
  );
};

export default ContactDetail;
