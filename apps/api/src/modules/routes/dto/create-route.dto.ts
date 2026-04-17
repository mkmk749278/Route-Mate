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
import { TrimString } from '../../shared/decorators/trim-string.decorator';

export class CreateRouteDto {
  @TrimString()
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  @Matches(/\S/)
  origin!: string;

  @TrimString()
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  @Matches(/\S/)
  destination!: string;

  @IsISO8601()
  travelDate!: string;

  @TrimString()
  @Matches(/^([01]\d|2[0-3]):[0-5]\d$/)
  preferredDepartureTime!: string;

  @IsOptional()
  @IsInt()
  @Min(1)
  seatCount?: number;

  @IsOptional()
  @TrimString()
  @IsString()
  @MaxLength(500)
  notes?: string;
}
