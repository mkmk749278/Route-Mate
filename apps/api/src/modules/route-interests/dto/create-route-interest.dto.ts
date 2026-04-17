import { IsUUID } from 'class-validator';

export class CreateRouteInterestDto {
  @IsUUID()
  routePostId!: string;
}
