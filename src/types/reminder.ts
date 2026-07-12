export interface Reminder {
  id: string;
  contactId: string | null; // null = general reminder (not linked to a contact)
  text: string;
  dueAt: string | null; // ISO date string, null = no deadline
  priority: 0 | 1 | 2; // 0 = low, 1 = normal, 2 = high
  isDone: boolean;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  notificationId: number | null; // ID for scheduled local notification
}

export interface ReminderFormData {
  contactId?: string | null;
  text: string;
  dueAt?: string | null;
  priority?: 0 | 1 | 2;
}

export const PRIORITY_LABELS: Record<0 | 1 | 2, string> = {
  0: "Низкий",
  1: "Обычный",
  2: "Высокий",
};

export const PRIORITY_COLORS: Record<0 | 1 | 2, string> = {
  0: "bg-gray-500",
  1: "bg-blue-500",
  2: "bg-red-500",
};
