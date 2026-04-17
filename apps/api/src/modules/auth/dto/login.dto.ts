import { Transform } from 'class-transformer';
import { IsEmail, IsString, MinLength } from 'class-validator';

const trimString = (value: unknown) =>
  typeof value === 'string' ? value.trim() : value;

export class LoginDto {
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsEmail()
  email!: string;

  @IsString()
  @MinLength(8)
  password!: string;
}
