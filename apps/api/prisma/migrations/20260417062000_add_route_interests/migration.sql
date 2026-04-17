-- CreateTable
CREATE TABLE "RouteInterest" (
    "id" UUID NOT NULL,
    "routePostId" UUID NOT NULL,
    "requesterUserId" UUID NOT NULL,
    "ownerUserId" UUID NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RouteInterest_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "RouteInterest_ownerUserId_createdAt_idx" ON "RouteInterest"("ownerUserId", "createdAt");

-- CreateIndex
CREATE INDEX "RouteInterest_requesterUserId_createdAt_idx" ON "RouteInterest"("requesterUserId", "createdAt");

-- CreateIndex
CREATE INDEX "RouteInterest_routePostId_requesterUserId_status_idx" ON "RouteInterest"("routePostId", "requesterUserId", "status");

-- AddForeignKey
ALTER TABLE "RouteInterest"
ADD CONSTRAINT "RouteInterest_routePostId_fkey"
FOREIGN KEY ("routePostId") REFERENCES "RoutePost"("id")
ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RouteInterest"
ADD CONSTRAINT "RouteInterest_requesterUserId_fkey"
FOREIGN KEY ("requesterUserId") REFERENCES "User"("id")
ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RouteInterest"
ADD CONSTRAINT "RouteInterest_ownerUserId_fkey"
FOREIGN KEY ("ownerUserId") REFERENCES "User"("id")
ON DELETE CASCADE ON UPDATE CASCADE;
