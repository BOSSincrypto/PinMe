import { LocalNotifications } from "@capacitor/local-notifications";
import type { ScheduleOptions } from "@capacitor/local-notifications";
import type { Reminder } from "@/types/reminder";
import { storage } from "./storage";

const MAX_NOTIFICATION_ID = 2147483647;
const issuedNotificationIds = new Set<number>();

const generateNotificationId = (): number => {
  const usedIds = new Set(
    storage.getReminders().flatMap((reminder) =>
      reminder.notificationId === null ? [] : [reminder.notificationId],
    ),
  );
  issuedNotificationIds.forEach((id) => usedIds.add(id));
  for (let attempt = 0; attempt < 32; attempt++) {
    const id = crypto.getRandomValues(new Uint32Array(1))[0] & MAX_NOTIFICATION_ID;
    if (id > 0 && !usedIds.has(id)) {
      issuedNotificationIds.add(id);
      return id;
    }
  }
  for (let id = 1; id <= MAX_NOTIFICATION_ID; id++) {
    if (!usedIds.has(id)) {
      issuedNotificationIds.add(id);
      return id;
    }
  }
  throw new Error("Не удалось создать идентификатор уведомления");
};

export const notificationService = {
  requestPermissions: async (): Promise<boolean> => {
    try {
      const result = await LocalNotifications.requestPermissions();
      return result.display === "granted";
    } catch (error) {
      console.error("Error requesting notification permissions:", error);
      return false;
    }
  },

  checkPermissions: async (): Promise<boolean> => {
    try {
      const result = await LocalNotifications.checkPermissions();
      return result.display === "granted";
    } catch (error) {
      console.error("Error checking notification permissions:", error);
      return false;
    }
  },

  scheduleReminder: async (reminder: Reminder, contactName?: string): Promise<number | null> => {
    if (!reminder.dueAt) {
      return null;
    }

    try {
      const hasPermission = await notificationService.checkPermissions();
      if (!hasPermission) {
        const granted = await notificationService.requestPermissions();
        if (!granted) {
          console.warn("Notification permissions not granted");
          return null;
        }
      }

      const notificationId = generateNotificationId();
      const dueDate = new Date(reminder.dueAt);

      if (Number.isNaN(dueDate.getTime()) || dueDate <= new Date()) {
        return null;
      }

      const title = contactName ? "Напоминание контакта" : "Напоминание";

      const options: ScheduleOptions = {
        notifications: [
          {
            id: notificationId,
            title,
            body: "Откройте PinMe, чтобы посмотреть подробности",
            schedule: { at: dueDate },
            sound: "default",
            extra: {
              reminderId: reminder.id,
              contactId: reminder.contactId,
            },
          },
        ],
      };

      await LocalNotifications.schedule(options);
      return notificationId;
    } catch (error) {
      console.error("Error scheduling notification:", error);
      return null;
    }
  },

  cancelNotification: async (notificationId: number): Promise<void> => {
    try {
      await LocalNotifications.cancel({ notifications: [{ id: notificationId }] });
    } catch (error) {
      console.error("Error canceling notification:", error);
    }
  },

  cancelReminderNotification: async (reminder: Reminder): Promise<void> => {
    if (reminder.notificationId !== null) {
      await notificationService.cancelNotification(reminder.notificationId);
    }
  },

  rescheduleReminder: async (reminder: Reminder, contactName?: string): Promise<number | null> => {
    if (reminder.notificationId !== null) {
      await notificationService.cancelNotification(reminder.notificationId);
    }

    if (reminder.dueAt && !reminder.isDone) {
      return await notificationService.scheduleReminder(reminder, contactName);
    }

    return null;
  },

  reconcileNotifications: async (): Promise<void> => {
    try {
      const reminders = storage.getReminders();
      const contacts = storage.getContacts();
      const contactMap = new Map(contacts.map((c) => [c.id, c.name]));

      const pending = await LocalNotifications.getPending();
      if (pending.notifications.length > 0) {
        await LocalNotifications.cancel({ notifications: pending.notifications });
      }

      for (const reminder of reminders) {
        if (reminder.dueAt && !reminder.isDone) {
          const dueDate = new Date(reminder.dueAt);
          if (dueDate > new Date()) {
            const contactName = reminder.contactId
              ? contactMap.get(reminder.contactId)
              : undefined;
            const notificationId = await notificationService.scheduleReminder(
              reminder,
              contactName
            );
            if (notificationId !== null && notificationId !== reminder.notificationId) {
              storage.updateReminder(reminder.id, {
                ...reminder,
                notificationId,
                updatedAt: new Date().toISOString(),
              });
            }
          }
        }
      }
    } catch (error) {
      console.error("Error reconciling notifications:", error);
    }
  },

  setupListeners: async (): Promise<() => Promise<void>> => {
    try {
      const handles = await Promise.all([
        LocalNotifications.addListener("localNotificationReceived", () => undefined),
        LocalNotifications.addListener("localNotificationActionPerformed", () => undefined),
      ]);
      return async () => {
        try {
          await Promise.all(handles.map((handle) => handle.remove()));
        } catch (error) {
          console.error("Error removing notification listeners:", error);
        }
      };
    } catch (error) {
      console.error("Error setting up notification listeners:", error);
      return async () => undefined;
    }
  },
};
