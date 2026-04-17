import {
  IsISO8601,
  IsOptional,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class DiscoverRoutesQueryDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(160)
  @Matches(/\S/)
  origin?: string;

  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(160)
  @Matches(/\S/)
  destination?: string;

  @IsOptional()
  @IsISO8601()
  travelDate?: string;
}
