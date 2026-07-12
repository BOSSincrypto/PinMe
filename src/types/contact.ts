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
}
