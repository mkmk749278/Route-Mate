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

export class CreateRouteDto {
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  origin!: string;

  @IsString()
  @MinLength(2)
  @MaxLength(160)
  destination!: string;

  @IsISO8601()
  travelDate!: string;

  @Matches(/^([01]\d|2[0-3]):[0-5]\d$/)
  preferredDepartureTime!: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  seatCount?: number;

  @IsOptional()
  @IsString()
  @MaxLength(500)
  notes?: string;
}
