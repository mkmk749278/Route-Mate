import { Transform } from 'class-transformer';
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
const trimString = (value: unknown) =>
  typeof value === 'string' ? value.trim() : value;

export class UpdateMeDto {
  @IsOptional()
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MinLength(2)
  @MaxLength(80)
  @Matches(/\S/)
  name?: string;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @Matches(/^\+?[1-9]\d{7,14}$/)
  phone?: string;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MinLength(2)
  @MaxLength(100)
  @Matches(/\S/)
  city?: string;

  @IsOptional()
  @IsIn(GENDER_VALUES)
  gender?: string;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MaxLength(240)
  bio?: string;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsUrl()
  @MaxLength(2048)
  avatarUrl?: string;
}
