import {
  IsInt,
  IsISO8601,
  IsOptional,
  IsString,
  Max,
  Min,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';
import { Type } from 'class-transformer';
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

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  @Max(1000)
  offset?: number;
}
