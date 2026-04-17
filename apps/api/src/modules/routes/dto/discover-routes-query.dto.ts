import {
  IsISO8601,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
} from 'class-validator';

export class DiscoverRoutesQueryDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  origin?: string;

  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(160)
  destination?: string;

  @IsOptional()
  @IsISO8601()
  travelDate?: string;
}
