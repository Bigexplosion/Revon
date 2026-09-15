const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();

async function main() {
  // Clear existing data
  await prisma.track.deleteMany();

  await prisma.track.createMany({
    data: [
      {
        code: 'T21',
        name: 'Xin-Zhongheng',
        nameZh: '新中橫',
        region: 'Chiayi',
        difficulty: 'EXTREME',
        distanceKm: 38.3,
        cornersCount: 142,
        coverImage: 'https://example.com/t21.jpg'
      },
      {
        code: 'T14',
        name: 'Puli - Wuling',
        nameZh: '台14線',
        region: 'Nantou',
        difficulty: 'HARD',
        distanceKm: 52.1,
        cornersCount: 98,
        coverImage: 'https://example.com/t14.jpg'
      }
    ]
  });

  console.log('Seed data created successfully');
}

main()
  .catch((e) => console.error(e))
  .finally(async () => await prisma.$disconnect());
