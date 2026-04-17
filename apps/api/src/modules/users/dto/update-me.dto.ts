import {
  IsIn,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  Matches,
  MinLength,
} from 'class-validator';
import { TrimString } from '../../shared/decorators/trim-string.decorator';

const GENDER_VALUES = ['male', 'female', 'non_binary', 'prefer_not_to_say'];

export class UpdateMeDto {
  @IsOptional()
  @TrimString()
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  @Matches(/\S/)
  name?: string;

  @IsOptional()
  @TrimString()
  @Matches(/^\+?[1-9]\d{7,14}$/)
  phone?: string;

  @IsOptional()
  @TrimString()
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  @Matches(/\S/)
  city?: string;

  @IsOptional()
  @IsIn(GENDER_VALUES)
  gender?: string;

  @IsOptional()
  @TrimString()
  @IsString()
  @MaxLength(240)
  bio?: string;

  @IsOptional()
  @TrimString()
  @IsUrl()
  @MaxLength(2048)
  avatarUrl?: string;
}
