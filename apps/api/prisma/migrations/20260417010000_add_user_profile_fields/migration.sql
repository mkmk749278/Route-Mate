-- AlterTable
ALTER TABLE "User"
ADD COLUMN "phone" TEXT,
ADD COLUMN "city" TEXT,
ADD COLUMN "gender" TEXT,
ADD COLUMN "bio" TEXT,
ADD COLUMN "avatarUrl" TEXT,
ADD COLUMN "isProfileComplete" BOOLEAN NOT NULL DEFAULT false;
