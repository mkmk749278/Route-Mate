-- CreateTable
CREATE TABLE "RoutePost" (
    "id" UUID NOT NULL,
    "userId" UUID NOT NULL,
    "origin" TEXT NOT NULL,
    "destination" TEXT NOT NULL,
    "travelDate" TIMESTAMP(3) NOT NULL,
    "preferredDepartureTime" TEXT NOT NULL,
    "seatCount" INTEGER,
    "notes" TEXT,
    "status" TEXT NOT NULL DEFAULT 'active',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RoutePost_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "RoutePost_userId_createdAt_idx" ON "RoutePost"("userId", "createdAt");

-- AddForeignKey
ALTER TABLE "RoutePost"
ADD CONSTRAINT "RoutePost_userId_fkey"
FOREIGN KEY ("userId") REFERENCES "User"("id")
ON DELETE CASCADE ON UPDATE CASCADE;
