import { useCallback, useEffect, useState } from "react";
import { storage } from "@/lib/storage";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { User, Calendar, Cake } from "lucide-react";
import { Link } from "react-router-dom";

interface DateEvent {
  id: string;
  contactId: string;
  contactName: string;
  contactAvatar?: string;
  title: string;
  date: string;
  daysLeft: number;
  type: "birthday" | "event";
}

const Dates = () => {
  const [events, setEvents] = useState<DateEvent[]>([]);

  const loadEvents = useCallback(() => {
    const contacts = storage.getContacts();
    const allEvents: DateEvent[] = [];
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    contacts.forEach((contact) => {
      // Add birthday
      if (contact.birthday) {
        const daysLeft = calculateDaysLeft(contact.birthday);
        allEvents.push({
          id: `birthday-${contact.id}`,
          contactId: contact.id,
          contactName: contact.name,
          contactAvatar: contact.avatar,
          title: "День рождения",
          date: contact.birthday,
          daysLeft,
          type: "birthday",
        });
      }

      // Add events
      if (contact.events) {
        contact.events.forEach((event) => {
          const daysLeft = calculateDaysLeft(event.date);
          allEvents.push({
            id: `event-${event.id}`,
            contactId: contact.id,
            contactName: contact.name,
            contactAvatar: contact.avatar,
            title: event.title,
            date: event.date,
            daysLeft,
            type: "event",
          });
        });
      }
    });

    // Sort by days left
    allEvents.sort((a, b) => a.daysLeft - b.daysLeft);
    setEvents(allEvents);
  }, []);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  const calculateDaysLeft = (dateString: string): number => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const targetDate = new Date(dateString);
    const currentYear = today.getFullYear();

    // Set the target date to current year
    targetDate.setFullYear(currentYear);

    // If the date has passed this year, use next year
    if (targetDate < today) {
      targetDate.setFullYear(currentYear + 1);
    }

    const diffTime = targetDate.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    return diffDays;
  };

  const calculateAge = (dateString: string): number => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const [birthYear, birthMonth, birthDay] = dateString.split("-").map(Number);
    const birthdayThisYear = new Date(today.getFullYear(), birthMonth - 1, birthDay);
    const birthdayYear = birthdayThisYear < today
      ? today.getFullYear() + 1
      : today.getFullYear();
    return birthdayYear - birthYear;
  };

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString("ru-RU", {
      day: "numeric",
      month: "long",
    });
  };

  const getDaysText = (days: number): string => {
    if (days === 0) return "Сегодня";
    if (days === 1) return "Завтра";
    if (days < 0) return "Прошло";

    const lastDigit = days % 10;
    const lastTwoDigits = days % 100;

    if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
      return `Через ${days} дней`;
    }

    if (lastDigit === 1) {
      return `Через ${days} день`;
    }

    if (lastDigit >= 2 && lastDigit <= 4) {
      return `Через ${days} дня`;
    }

    return `Через ${days} дней`;
  };

  return (
    <div className="min-h-screen bg-background pb-20 p-4 md:p-6 lg:p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <header className="space-y-2">
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <Calendar className="w-8 h-8" />
            Даты
          </h1>
          <p className="text-muted-foreground">
            Дни рождения и важные события
          </p>
        </header>

        <div className="space-y-3">
          {events.length === 0 ? (
            <div className="text-center py-12">
              <Calendar className="w-16 h-16 mx-auto mb-4 text-muted-foreground" />
              <p className="text-muted-foreground text-lg">
                Нет запланированных событий
              </p>
              <p className="text-muted-foreground text-sm mt-2">
                Добавьте дни рождения и события к контактам
              </p>
            </div>
          ) : (
            events.map((event) => (
              <Link key={event.id} to={`/contact/${event.contactId}`}>
                <Card className="hover:shadow-lg transition-all duration-300 hover:scale-[1.02] cursor-pointer">
                  <CardContent className="p-4 sm:p-6">
                    <div className="flex items-start gap-3 sm:gap-4">
                      <Avatar className="flex-shrink-0 w-12 h-12 sm:w-14 sm:h-14">
                        {event.contactAvatar ? (
                          <AvatarImage
                            src={event.contactAvatar}
                            alt={event.contactName}
                          />
                        ) : (
                          <AvatarFallback>
                            <User className="w-6 h-6 sm:w-7 sm:h-7" />
                          </AvatarFallback>
                        )}
                      </Avatar>
                      <div className="flex-1 min-w-0">
                        <h3 className="font-semibold text-base sm:text-lg mb-1 truncate">
                          {event.contactName}
                        </h3>
                        <div className="flex items-center gap-2 mb-2">
                          {event.type === "birthday" ? (
                            <Cake className="w-4 h-4 text-primary flex-shrink-0" />
                          ) : (
                            <Calendar className="w-4 h-4 text-primary flex-shrink-0" />
                          )}
                          <span className="text-sm text-muted-foreground truncate">
                            {event.title}
                            {event.type === "birthday" && (
                              <span className="ml-1">
                                — {calculateAge(event.date)} лет
                              </span>
                            )}
                          </span>
                        </div>
                        <div className="flex flex-wrap gap-2 items-center">
                          <Badge
                            variant={
                              event.daysLeft === 0
                                ? "default"
                                : event.daysLeft <= 7
                                ? "destructive"
                                : "secondary"
                            }
                            className="text-xs"
                          >
                            {getDaysText(event.daysLeft)}
                          </Badge>
                          <span className="text-xs text-muted-foreground">
                            {formatDate(event.date)}
                          </span>
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </Link>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default Dates;
