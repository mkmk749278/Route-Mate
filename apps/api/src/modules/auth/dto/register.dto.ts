import {
  IsEmail,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';
import { TrimString } from '../../shared/decorators/trim-string.decorator';

export class RegisterDto {
  @TrimString()
  @IsEmail()
  email!: string;

  @TrimString()
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  @Matches(/\S/)
  name!: string;

  @IsString()
  @MinLength(8)
  password!: string;
}
