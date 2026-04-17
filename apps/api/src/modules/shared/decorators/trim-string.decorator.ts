import { Transform } from 'class-transformer';
import { trimString } from '../utils/trim-string.util';

export const TrimString = () =>
  Transform(({ value }: { value: unknown }) => trimString(value));
