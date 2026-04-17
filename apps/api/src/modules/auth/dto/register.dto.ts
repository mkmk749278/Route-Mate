import { Transform } from 'class-transformer';
import {
  IsEmail,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

const trimString = (value: unknown) =>
  typeof value === 'string' ? value.trim() : value;

export class RegisterDto {
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsEmail()
  email!: string;

  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  @Matches(/\S/)
  name!: string;

  @IsString()
  @MinLength(8)
  password!: string;
}
