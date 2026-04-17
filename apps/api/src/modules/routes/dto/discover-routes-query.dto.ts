import {
  IsISO8601,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';
import { TrimString } from '../../shared/decorators/trim-string.decorator';

export class DiscoverRoutesQueryDto {
  @IsOptional()
  @TrimString()
  @IsString()
  @MinLength(1)
  @MaxLength(160)
  @Matches(/\S/)
  origin?: string;

  @IsOptional()
  @TrimString()
  @IsString()
  @MinLength(1)
  @MaxLength(160)
  @Matches(/\S/)
  destination?: string;

  @IsOptional()
  @IsISO8601()
  travelDate?: string;
}
