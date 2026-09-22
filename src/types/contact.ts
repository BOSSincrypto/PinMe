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
