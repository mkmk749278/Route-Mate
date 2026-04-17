import { IsEmail, IsString, MinLength } from 'class-validator';
import { TrimString } from '../../shared/decorators/trim-string.decorator';

export class LoginDto {
  @TrimString()
  @IsEmail()
  email!: string;

  @IsString()
  @MinLength(8)
  password!: string;
}
