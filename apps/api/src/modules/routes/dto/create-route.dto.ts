import { Transform } from 'class-transformer';
import {
  IsInt,
  IsISO8601,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

const trimString = (value: unknown) =>
  typeof value === 'string' ? value.trim() : value;

export class CreateRouteDto {
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  @Matches(/\S/)
  origin!: string;

  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  @Matches(/\S/)
  destination!: string;

  @IsISO8601()
  travelDate!: string;

  @Transform(({ value }: { value: unknown }) => trimString(value))
  @Matches(/^([01]\d|2[0-3]):[0-5]\d$/)
  preferredDepartureTime!: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  seatCount?: number;

  @IsOptional()
  @Transform(({ value }: { value: unknown }) => trimString(value))
  @IsString()
  @MaxLength(500)
  notes?: string;
}
