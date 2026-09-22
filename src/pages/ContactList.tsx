import { useState, useEffect } from "react";
import { Link } from "react-router";
import { storage } from "@/lib/storage";
import {
  Contact,
  HELP_TROUBLE_LABELS,
  HELP_TROUBLE_OPTIONS,
  HelpTroubleStatus,
} from "@/types/contact";
import { ContactCard } from "@/components/ContactCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Plus, Search } from "lucide-react";

const HELP_TROUBLE_FILTER_CLASSES: Record<HelpTroubleStatus, string> = {
  yes: "bg-green-500 hover:bg-green-500 text-white",
  no: "bg-red-500 hover:bg-red-500 text-white",
  unsure: "bg-amber-500 hover:bg-amber-500 text-white",
};

const ContactList = () => {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [helpFilter, setHelpFilter] = useState<HelpTroubleStatus | null>(null);

  useEffect(() => {
    loadContacts();
  }, []);

  const loadContacts = () => {
    const data = storage.getContacts();
    setContacts(data);
  };

  const filteredContacts = contacts.filter((contact) => {
    if (helpFilter && contact.helpInTrouble !== helpFilter) {
      return false;
    }
    const query = searchQuery.toLowerCase();
    return (
      contact.name.toLowerCase().includes(query) ||
      contact.phone?.toLowerCase().includes(query) ||
      contact.email?.toLowerCase().includes(query) ||
      contact.workplace?.toLowerCase().includes(query) ||
      contact.position?.toLowerCase().includes(query) ||
      contact.source?.toLowerCase().includes(query) ||
      contact.tags?.some(tag => tag.name.toLowerCase().includes(query))
    );
  });

  return (
    <div className="min-h-screen bg-background pb-20 p-4 md:p-6 lg:p-8">
      <div className="max-w-4xl mx-auto space-y-6">
        <header className="space-y-4">
          <div className="flex items-center justify-between">
            <h1 className="text-3xl font-bold">Мои Контакты</h1>
            <Link to="/add-contact">
              <Button>
                <Plus className="w-5 h-5 mr-2" />
                Добавить
              </Button>
            </Link>
          </div>

          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
            <Input
              placeholder="Поиск контактов..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>

          <div
            className="flex flex-wrap items-center gap-2"
            role="group"
            aria-label="Фильтр по полю «Поможет ли в трудной ситуации»"
          >
            {HELP_TROUBLE_OPTIONS.map((option) => (
              <button
                key={option}
                type="button"
                aria-pressed={helpFilter === option}
                onClick={() =>
                  setHelpFilter(helpFilter === option ? null : option)
                }
              >
                <Badge
                  className={`cursor-pointer transition-opacity ${
                    HELP_TROUBLE_FILTER_CLASSES[option]
                  } ${helpFilter === option ? "opacity-100" : "opacity-40"}`}
                >
                  {HELP_TROUBLE_LABELS[option]}
                </Badge>
              </button>
            ))}
          </div>
        </header>

        <div className="space-y-3">
          {filteredContacts.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-muted-foreground text-lg">
                {searchQuery || helpFilter
                  ? "Контакты не найдены"
                  : "У вас пока нет контактов"}
              </p>
              {!searchQuery && !helpFilter && (
                <Link to="/add-contact">
                  <Button className="mt-4">
                    <Plus className="w-5 h-5 mr-2" />
                    Добавить первый контакт
                  </Button>
                </Link>
              )}
            </div>
          ) : (
            filteredContacts.map((contact) => (
              <ContactCard key={contact.id} contact={contact} />
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default ContactList;
