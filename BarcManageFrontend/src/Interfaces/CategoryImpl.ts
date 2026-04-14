export interface CategoryImpl {
  id: string;
  parent_id: string | null;
  name: string;
  level: number;
  sort: number;
  is_enabled: boolean;
  icon: string | null;
  children: Array<CategoryImpl>;
}
