export const generateCampaignPrefix = (name: string) => {
  const now = new Date();
  const monthNames = [
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
  ];

  const month = monthNames[now.getMonth()];
  const day = now.getDate().toString().padStart(2, "0");
  const year = now.getFullYear().toString().slice(-2);

  return `${name}_${month}_${day}_${year}_`;
};
