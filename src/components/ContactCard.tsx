import { Contact } from "@/types/contact";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { User, Phone, Mail, Briefcase, Calendar, Cake } from "lucide-react";
import { Link } from "react-router-dom";
import { format } from "date-fns";
import { ru } from "date-fns/locale";

interface ContactCardProps {
  contact: Contact;
}

export const ContactCard = ({ contact }: ContactCardProps) => {
  return (
    <Link to={`/contact/${contact.id}`}>
      <Card className="hover:shadow-lg transition-all duration-300 hover:scale-[1.02] cursor-pointer">
        <CardContent className="p-4 sm:p-6">
          <div className="flex items-start gap-3 sm:gap-4">
            <Avatar className="flex-shrink-0 w-12 h-12 sm:w-14 sm:h-14">
              {contact.avatar ? (
                <AvatarImage src={contact.avatar} alt={contact.name} />
              ) : (
                <AvatarFallback>
                  <User className="w-6 h-6 sm:w-7 sm:h-7" />
                </AvatarFallback>
              )}
            </Avatar>
            <div className="flex-1 min-w-0">
              <h3 className="font-semibold text-base sm:text-lg mb-2 truncate">{contact.name}</h3>
              {contact.tags && contact.tags.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mb-2">
                  {contact.tags.map((tag) => (
                    <Badge
                      key={tag.id}
                      style={{ backgroundColor: tag.color }}
                      className="text-white text-xs px-2 py-0.5"
                    >
                      {tag.name}
                    </Badge>
                  ))}
                </div>
              )}
              <div className="space-y-1.5">
                {contact.workplace && (
                  <div className="flex items-center gap-2 text-muted-foreground text-xs sm:text-sm">
                    <Briefcase className="w-3.5 h-3.5 sm:w-4 sm:h-4 flex-shrink-0" />
                    <span className="truncate">{contact.workplace}</span>
                  </div>
                )}
                {contact.phone && (
                  <div className="flex items-center gap-2 text-muted-foreground text-xs sm:text-sm">
                    <Phone className="w-3.5 h-3.5 sm:w-4 sm:h-4 flex-shrink-0" />
                    <span
                      className="truncate hover:text-primary transition-colors cursor-pointer"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        window.location.href = `tel:${contact.phone}`;
                      }}
                    >
                      {contact.phone}
                    </span>
                  </div>
                )}
                {contact.email && (
                  <div className="flex items-center gap-2 text-muted-foreground text-xs sm:text-sm">
                    <Mail className="w-3.5 h-3.5 sm:w-4 sm:h-4 flex-shrink-0" />
                    <span className="truncate">{contact.email}</span>
                  </div>
                )}
                {contact.birthday && (
                  <div className="flex items-center gap-2 text-muted-foreground text-xs sm:text-sm">
                    <Cake className="w-3.5 h-3.5 sm:w-4 sm:h-4 flex-shrink-0" />
                    <span className="truncate">
                      {format(new Date(contact.birthday), "d MMMM", { locale: ru })}
                    </span>
                  </div>
                )}
                {contact.events && contact.events.length > 0 && (
                  <div className="flex items-center gap-2 text-muted-foreground text-xs sm:text-sm">
                    <Calendar className="w-3.5 h-3.5 sm:w-4 sm:h-4 flex-shrink-0" />
                    <span className="truncate">
                      События: {contact.events.length}
                    </span>
                  </div>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
};
