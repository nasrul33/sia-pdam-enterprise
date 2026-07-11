import { z } from "zod";

export const entityOptionSchema = z.object({
  id: z.string().uuid(),
  label: z.string().min(1),
  description: z.string().min(1).optional(),
  status: z.string().min(1).optional()
});

export const entityOptionListSchema = z.array(entityOptionSchema);

export type LookupEntityOption = z.infer<typeof entityOptionSchema>;
