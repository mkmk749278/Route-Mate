import {
  IsIn,
  IsOptional,
  IsString,
  IsUrl,
  MaxLength,
  Matches,
  MinLength,
} from 'class-validator';

const GENDER_VALUES = ['male', 'female', 'non_binary', 'prefer_not_to_say'];

export class UpdateMeDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  name?: string;

  @IsOptional()
  @Matches(/^\+?[1-9]\d{7,14}$/)
  phone?: string;

  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  city?: string;

  @IsOptional()
  @IsIn(GENDER_VALUES)
  gender?: string;

  @IsOptional()
  @IsString()
  @MaxLength(240)
  bio?: string;

  @IsOptional()
  @IsUrl()
  @MaxLength(2048)
  avatarUrl?: string;
}
