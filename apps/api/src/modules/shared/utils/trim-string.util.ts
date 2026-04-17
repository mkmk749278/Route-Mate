export const trimString = (value: unknown) =>
  typeof value === 'string' ? value.trim() : value;
