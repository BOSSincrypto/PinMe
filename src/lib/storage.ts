import { z } from "zod";
import type { Contact } from "@/types/contact";
import type { Reminder } from "@/types/reminder";
import {
  MAX_PASSWORD_HASH_ITERATIONS,
  MAX_PASSWORD_LENGTH,
  MIN_PASSWORD_HASH_ITERATIONS,
} from "@/lib/encryption";

const STORAGE_KEY = "secure-contacts";
const REMINDERS_STORAGE_KEY = "secure-reminders";
const MIN_NOTIFICATION_ID = -2147483648;
const MAX_NOTIFICATION_ID = 2147483647;

const isValidDateOnly = (value: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const date = new Date(`${value}T00:00:00.000Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
};

const dateOnlySchema = z.string().refine(isValidDateOnly);
const optionalTextSchema = z.string().optional();
export const isSafeExternalUrl = (value: string): boolean => {
  try {
    const protocol = new URL(value.trim()).protocol;
    return protocol === "http:" || protocol === "https:";
  } catch {
    return false;
  }
};
const tagSchema = z.object({
  id: z.string().min(1),
  name: z.string().trim().min(1),
  color: z.string().min(1),
}).strict();
const socialMediaSchema = z.object({
  platform: z.string(),
  url: z.string().trim().transform((value) => value === "" || isSafeExternalUrl(value) ? value : ""),
}).strict();
const eventSchema = z.object({
  id: z.string().min(1),
  title: z.string().trim().min(1),
  date: dateOnlySchema,
}).strict();

const contactSchema = z.object({
  id: z.string().min(1),
  name: z.string().trim().min(1),
  phone: optionalTextSchema,
  email: optionalTextSchema,
  workplace: optionalTextSchema,
  position: optionalTextSchema,
  source: optionalTextSchema,
  passwordHash: z.string().min(1).max(256).optional(),
  passwordSalt: z.string().min(1).max(256).optional(),
  passwordIterations: z.number().int().min(MIN_PASSWORD_HASH_ITERATIONS).max(MAX_PASSWORD_HASH_ITERATIONS).optional(),
  password: z.string().min(1).max(MAX_PASSWORD_LENGTH).optional(),
  notes: optionalTextSchema,
  avatar: optionalTextSchema,
  tags: z.array(tagSchema).optional(),
  birthday: z.union([z.literal(""), dateOnlySchema]).optional(),
  socialMedia: z.array(socialMediaSchema).optional(),
  events: z.array(eventSchema).optional(),
  additionalInfo: z.record(z.string()).optional(),
  createdAt: z.string().datetime({ offset: true }),
  updatedAt: z.string().datetime({ offset: true }),
}).strict().superRefine((contact, context) => {
  const digestValues = [contact.passwordHash, contact.passwordSalt, contact.passwordIterations];
  const hasAnyDigestValue = digestValues.some((value) => value !== undefined);
  const hasCompleteDigest = digestValues.every((value) => value !== undefined);
  if ((hasAnyDigestValue && !hasCompleteDigest) || (!hasCompleteDigest && !contact.password)) {
    context.addIssue({ code: z.ZodIssueCode.custom, message: "Некорректные данные пароля" });
  }
}).transform((contact) => contact as unknown as Contact);

const reminderSchema = z.object({
  id: z.string().min(1),
  contactId: z.string().min(1).nullable(),
  text: z.string().trim().min(1),
  dueAt: z.string().datetime({ offset: true }).nullable(),
  priority: z.union([z.literal(0), z.literal(1), z.literal(2)]),
  isDone: z.boolean(),
  completedAt: z.string().datetime({ offset: true }).nullable(),
  createdAt: z.string().datetime({ offset: true }),
  updatedAt: z.string().datetime({ offset: true }),
  notificationId: z.number().int().safe().nullable(),
}).strict();

const contactListSchema = z.array(contactSchema);
const backupSchema = z.object({
  contacts: contactListSchema,
  reminders: z.array(reminderSchema).optional(),
}).strict();

const saveJson = (key: string, value: unknown): boolean => {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch (error) {
    console.error("Error saving data:", error);
    return false;
  }
};

const restoreValue = (key: string, value: string | null): void => {
  if (value === null) {
    localStorage.removeItem(key);
  } else {
    localStorage.setItem(key, value);
  }
};

const saveVaultData = (contacts: Contact[], reminders: Reminder[]): boolean => {
  const previousContacts = localStorage.getItem(STORAGE_KEY);
  const previousReminders = localStorage.getItem(REMINDERS_STORAGE_KEY);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(contacts));
    localStorage.setItem(REMINDERS_STORAGE_KEY, JSON.stringify(reminders));
    return true;
  } catch (error) {
    console.error("Error saving data:", error);
    try {
      restoreValue(STORAGE_KEY, previousContacts);
      restoreValue(REMINDERS_STORAGE_KEY, previousReminders);
    } catch (rollbackError) {
      console.error("Error restoring data:", rollbackError);
    }
    return false;
  }
};

const parseBackup = (value: unknown): { contacts: Contact[]; reminders: Reminder[] } | null => {
  const parsed = Array.isArray(value)
    ? contactListSchema.safeParse(value)
    : backupSchema.safeParse(value);
  if (!parsed.success) {
    return null;
  }

  const contacts = Array.isArray(value)
    ? parsed.data as Contact[]
    : (parsed.data as { contacts: Contact[] }).contacts;
  const rawReminders = Array.isArray(value)
    ? []
    : ((parsed.data as { reminders?: Reminder[] }).reminders ?? []);
  const reminders = rawReminders.map((reminder) => ({
    ...reminder,
    notificationId:
      reminder.notificationId !== null &&
      (reminder.notificationId < MIN_NOTIFICATION_ID || reminder.notificationId > MAX_NOTIFICATION_ID)
        ? null
        : reminder.notificationId,
  }));
  const contactIds = new Set(contacts.map((contact) => contact.id));
  const reminderIds = new Set(reminders.map((reminder) => reminder.id));
  if (
    contactIds.size !== contacts.length ||
    reminderIds.size !== reminders.length ||
    reminders.some((reminder) => reminder.contactId !== null && !contactIds.has(reminder.contactId))
  ) {
    return null;
  }
  return { contacts, reminders };
};

export const storage = {
  getContacts: (): Contact[] => {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      if (!data) {
        return [];
      }
      const parsed = contactListSchema.safeParse(JSON.parse(data));
      return parsed.success ? parsed.data : [];
    } catch (error) {
      console.error("Error reading contacts:", error);
      return [];
    }
  },

  saveContacts: (contacts: Contact[]): boolean => saveJson(STORAGE_KEY, contacts),

  addContact: (contact: Contact): boolean => {
    const contacts = storage.getContacts();
    contacts.push(contact);
    return storage.saveContacts(contacts);
  },

  updateContact: (id: string, updatedContact: Contact): boolean => {
    const contacts = storage.getContacts();
    const index = contacts.findIndex((contact) => contact.id === id);
    if (index === -1) {
      return false;
    }
    contacts[index] = updatedContact;
    return storage.saveContacts(contacts);
  },

  deleteContact: (id: string): boolean => {
    const contacts = storage.getContacts().filter((contact) => contact.id !== id);
    const reminders = storage.getReminders().filter((reminder) => reminder.contactId !== id);
    return saveVaultData(contacts, reminders);
  },

  getContact: (id: string): Contact | undefined => {
    const contacts = storage.getContacts();
    return contacts.find((contact) => contact.id === id);
  },

  exportData: (): string => JSON.stringify(storage.getContacts(), null, 2),

  importData: (jsonData: string): boolean => {
    try {
      const parsed = contactListSchema.safeParse(JSON.parse(jsonData));
      return parsed.success && storage.saveContacts(parsed.data);
    } catch (error) {
      console.error("Error importing data:", error);
      return false;
    }
  },

  getReminders: (): Reminder[] => {
    try {
      const data = localStorage.getItem(REMINDERS_STORAGE_KEY);
      if (!data) {
        return [];
      }
      const parsed = z.array(reminderSchema).safeParse(JSON.parse(data));
      return parsed.success ? parsed.data : [];
    } catch (error) {
      console.error("Error reading reminders:", error);
      return [];
    }
  },

  saveReminders: (reminders: Reminder[]): boolean => saveJson(REMINDERS_STORAGE_KEY, reminders),

  addReminder: (reminder: Reminder): boolean => {
    const reminders = storage.getReminders();
    reminders.push(reminder);
    return storage.saveReminders(reminders);
  },

  updateReminder: (id: string, updatedReminder: Reminder): boolean => {
    const reminders = storage.getReminders();
    const index = reminders.findIndex((reminder) => reminder.id === id);
    if (index === -1) {
      return false;
    }
    reminders[index] = updatedReminder;
    return storage.saveReminders(reminders);
  },

  deleteReminder: (id: string): boolean => {
    const reminders = storage.getReminders();
    return storage.saveReminders(reminders.filter((reminder) => reminder.id !== id));
  },

  getReminder: (id: string): Reminder | undefined => {
    const reminders = storage.getReminders();
    return reminders.find((reminder) => reminder.id === id);
  },

  getRemindersByContact: (contactId: string): Reminder[] => {
    const reminders = storage.getReminders();
    return reminders.filter((reminder) => reminder.contactId === contactId);
  },

  getGeneralReminders: (): Reminder[] => {
    const reminders = storage.getReminders();
    return reminders.filter((reminder) => reminder.contactId === null);
  },

  toggleReminderDone: (id: string): Reminder | undefined => {
    const reminders = storage.getReminders();
    const index = reminders.findIndex((reminder) => reminder.id === id);
    if (index === -1) {
      return undefined;
    }
    const now = new Date().toISOString();
    reminders[index] = {
      ...reminders[index],
      isDone: !reminders[index].isDone,
      completedAt: !reminders[index].isDone ? now : null,
      updatedAt: now,
    };
    return storage.saveReminders(reminders) ? reminders[index] : undefined;
  },

  exportAllData: (): string => JSON.stringify({
    contacts: storage.getContacts(),
    reminders: storage.getReminders(),
  }, null, 2),

  importAllData: (jsonData: string): boolean => {
    try {
      const parsed = parseBackup(JSON.parse(jsonData));
      return parsed !== null && saveVaultData(parsed.contacts, parsed.reminders);
    } catch (error) {
      console.error("Error importing data:", error);
      return false;
    }
  },
};
