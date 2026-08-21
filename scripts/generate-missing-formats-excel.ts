import * as XLSX from 'xlsx';
import * as fs from 'fs';
import * as path from 'path';

// Read the CSV file
const csvPath = path.join(process.cwd(), 'attached_assets', 'OOH_Missing_Formats_To_Add.csv');
const csvContent = fs.readFileSync(csvPath, 'utf-8');

// Parse CSV
const lines = csvContent.split('\n');
const headers = lines[0].split(',');
const data = lines.slice(1).filter(line => line.trim()).map(line => {
  const values = line.split(',');
  return values;
});

// Create worksheet data
const worksheetData = [headers, ...data];

// Create workbook and worksheet
const wb = XLSX.utils.book_new();
const ws = XLSX.utils.aoa_to_sheet(worksheetData);

// Set column widths to match the original Google Sheet
ws['!cols'] = [
  { wch: 18 },  // Classification
  { wch: 18 },  // Type
  { wch: 35 },  // Format
  { wch: 20 },  // Category
  { wch: 22 },  // Scale
  { wch: 15 },  // Size_Category
  { wch: 28 },  // Typical_Dimensions_ft
  { wch: 18 },  // Orientation
  { wch: 40 },  // Typical_Viewing_Context
  { wch: 50 }   // Notes
];

// Add worksheet to workbook
XLSX.utils.book_append_sheet(wb, ws, 'Missing Formats');

// Create summary
const summary = data.reduce((acc, row) => {
  const classification = row[0];
  const type = row[1];
  const category = row[3];
  const sizeCategory = row[5];
  
  acc.byClassification[classification] = (acc.byClassification[classification] || 0) + 1;
  acc.byType[type] = (acc.byType[type] || 0) + 1;
  acc.byCategory[category] = (acc.byCategory[category] || 0) + 1;
  acc.bySize[sizeCategory] = (acc.bySize[sizeCategory] || 0) + 1;
  
  return acc;
}, {
  byClassification: {} as Record<string, number>,
  byType: {} as Record<string, number>,
  byCategory: {} as Record<string, number>,
  bySize: {} as Record<string, number>
});

const summaryData = [
  ['MISSING FORMATS SUMMARY'],
  [''],
  ['Total Missing Formats:', data.length],
  [''],
  ['BY CLASSIFICATION'],
  ...Object.entries(summary.byClassification).map(([key, value]) => [key, value]),
  [''],
  ['BY TYPE'],
  ...Object.entries(summary.byType).map(([key, value]) => [key, value]),
  [''],
  ['BY CATEGORY'],
  ...Object.entries(summary.byCategory).map(([key, value]) => [key, value]),
  [''],
  ['BY SIZE CATEGORY'],
  ...Object.entries(summary.bySize).map(([key, value]) => [key, value]),
  [''],
  ['INSTRUCTIONS'],
  ['1. Open your Google Sheet: https://docs.google.com/spreadsheets/d/1-nowN-yWB2RqA9uYWoUOMptCOZ7S_4dytb_5gUk530U'],
  ['2. Select all rows from the "Missing Formats" sheet (excluding header)'],
  ['3. Copy (Ctrl+C or Cmd+C)'],
  ['4. Go to your Google Sheet and paste at the end of your existing data'],
  ['5. All formats will maintain your exact template structure'],
];

const wsSummary = XLSX.utils.aoa_to_sheet(summaryData);
wsSummary['!cols'] = [{ wch: 80 }, { wch: 15 }];
XLSX.utils.book_append_sheet(wb, wsSummary, 'Summary & Instructions');

// Write file
const outputPath = path.join(process.cwd(), 'attached_assets', 'OOH_Missing_Formats_To_Add.xlsx');
XLSX.writeFile(wb, outputPath);

console.log(`✅ Excel file created successfully!`);
console.log(`📁 Location: ${outputPath}`);
console.log(`📊 Total Missing Formats: ${data.length}`);
console.log(`\nBreakdown:`);
console.log(`- Classic (Static): ${summary.byClassification['Classic (Static)'] || 0}`);
console.log(`- Digital (DOOH): ${summary.byClassification['Digital (DOOH)'] || 0}`);
console.log(`\nTop Types:`);
Object.entries(summary.byType)
  .sort((a, b) => (b[1] as number) - (a[1] as number))
  .slice(0, 5)
  .forEach(([type, count]) => console.log(`- ${type}: ${count}`));
