import { useState } from "react";
import { X, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Tag } from "@/types/contact";

interface TagManagerProps {
  tags: Tag[];
  onChange: (tags: Tag[]) => void;
}

const TAG_COLORS = [
  "#ef4444", "#f97316", "#f59e0b", "#84cc16", "#22c55e",
  "#10b981", "#14b8a6", "#06b6d4", "#0ea5e9", "#3b82f6",
  "#6366f1", "#8b5cf6", "#a855f7", "#d946ef", "#ec4899"
];

export const TagManager = ({ tags, onChange }: TagManagerProps) => {
  const [newTagName, setNewTagName] = useState("");
  const [selectedColor, setSelectedColor] = useState(TAG_COLORS[0]);

  const addTag = () => {
    if (newTagName.trim()) {
      const newTag: Tag = {
        id: Date.now().toString(),
        name: newTagName.trim(),
        color: selectedColor,
      };
      onChange([...tags, newTag]);
      setNewTagName("");
    }
  };

  const removeTag = (tagId: string) => {
    onChange(tags.filter((t) => t.id !== tagId));
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {tags.map((tag) => (
          <Badge
            key={tag.id}
            style={{ backgroundColor: tag.color }}
            className="text-white pl-3 pr-1 py-1 flex items-center gap-1"
          >
            {tag.name}
            <button
              type="button"
              onClick={() => removeTag(tag.id)}
              className="ml-1 rounded-full hover:bg-white/20 p-0.5"
            >
              <X className="h-3 w-3" />
            </button>
          </Badge>
        ))}
      </div>

      <div className="space-y-3">
        <Input
          placeholder="Название тега"
          value={newTagName}
          onChange={(e) => setNewTagName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              addTag();
            }
          }}
        />

        <div className="flex flex-wrap gap-2">
          {TAG_COLORS.map((color) => (
            <button
              key={color}
              type="button"
              onClick={() => setSelectedColor(color)}
              className={`w-8 h-8 rounded-full transition-transform ${
                selectedColor === color ? "ring-2 ring-offset-2 ring-primary scale-110" : ""
              }`}
              style={{ backgroundColor: color }}
            />
          ))}
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={addTag}
          className="w-full"
        >
          <Plus className="h-4 w-4 mr-2" />
          Добавить тег
        </Button>
      </div>
    </div>
  );
};
