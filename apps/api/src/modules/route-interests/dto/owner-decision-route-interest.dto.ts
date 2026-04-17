import { IsIn } from 'class-validator';

export class OwnerDecisionRouteInterestDto {
  @IsIn(['accepted', 'rejected'])
  status!: 'accepted' | 'rejected';
}
