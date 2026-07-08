import { z } from "zod";

export const currentUserSchema = z.object({
  username: z.string().min(1).nullable(),
  authenticated: z.boolean(),
  authorities: z.array(z.string().min(1))
});

export type CurrentUser = z.infer<typeof currentUserSchema>;
