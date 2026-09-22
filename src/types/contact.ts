export const HELP_TROUBLE_OPTIONS = ["yes", "no", "unsure"] as const;

export type HelpTroubleStatus = (typeof HELP_TROUBLE_OPTIONS)[number];

export const HELP_TROUBLE_LABELS: Record<HelpTroubleStatus, string> = {
  yes: "Да",
  no: "Нет",
  unsure: "Не знаю",
};

export const HELP_TROUBLE_BADGE_CLASSES: Record<HelpTroubleStatus, string> = {
  yes: "bg-green-500 hover:bg-green-500 text-white",
  no: "bg-red-500 hover:bg-red-500 text-white",
  unsure: "bg-amber-500 hover:bg-amber-500 text-white",
};

export const DEBT_DIRECTIONS = ["i_owe", "owed_to_me"] as const;

export type DebtDirection = (typeof DEBT_DIRECTIONS)[number];

export const DEBT_DIRECTION_LABELS: Record<DebtDirection, string> = {
  i_owe: "Я должен",
  owed_to_me: "Мне должны",
};

export const DEBT_DIRECTION_FILTER_CLASSES: Record<DebtDirection, string> = {
  i_owe: "bg-orange-500 hover:bg-orange-500 text-white",
  owed_to_me: "bg-emerald-500 hover:bg-emerald-500 text-white",
};

export interface DebtEntry {
  id: string;
  direction: DebtDirection;
  description: string;
  amount?: number;
  isOpen: boolean;
  createdAt: string;
  updatedAt: string;
}

export const formatDebtAmount = (amount: number): string => {
  const hasCents = Math.round(amount * 100) % 100 !== 0;
  return `${amount.toLocaleString("ru-RU", {
    minimumFractionDigits: hasCents ? 2 : 0,
    maximumFractionDigits: 2,
  })} ₽`;
};

export const getOpenDebtsTotal = (
  debts: DebtEntry[] | undefined,
  direction?: DebtDirection
): number =>
  (debts ?? [])
    .filter(
      (debt) =>
        debt.isOpen && (direction === undefined || debt.direction === direction)
    )
    .reduce((sum, debt) => sum + (debt.amount ?? 0), 0);

export interface Tag {
  id: string;
  name: string;
  color: string;
}

export interface SocialMedia {
  platform: string;
  url: string;
}

export interface ContactEvent {
  id: string;
  title: string;
  date: string;
}

export interface Contact {
  id: string;
  name: string;
  phone?: string;
  email?: string;
  workplace?: string;
  position?: string;
  source?: string;
  passwordHash: string;
  passwordSalt: string;
  passwordIterations: number;
  password?: string;
  notes?: string;
  avatar?: string;
  tags?: Tag[];
  birthday?: string;
  socialMedia?: SocialMedia[];
  events?: ContactEvent[];
  additionalInfo?: Record<string, string>;
  helpInTrouble: HelpTroubleStatus;
  debts?: DebtEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface ContactFormData {
  name: string;
  phone?: string;
  email?: string;
  workplace?: string;
  position?: string;
  source?: string;
  password: string;
  notes?: string;
  avatar?: string;
  tags?: Tag[];
  birthday?: string;
  socialMedia?: SocialMedia[];
  events?: ContactEvent[];
  additionalInfo?: Record<string, string>;
  helpInTrouble: HelpTroubleStatus;
}
