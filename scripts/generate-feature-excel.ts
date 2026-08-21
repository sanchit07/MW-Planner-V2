import * as XLSX from 'xlsx';

interface FeatureEntry {
  level: number;
  hierarchy: string;
  featureName: string;
  description: string;
  location: string;
  screenshotRef: string;
  defaultFunctions: string;
}

// Function categories that map to features:
// Media Owner Functions: Operations Manager, Creative Approver, Sales Rep, Billing Admin, Viewer
// Media Agency Functions: Campaign Planner, Media Buyer, Account Director, Creative Manager, Viewer
// Advertiser Functions: Brand Manager, Approver, Viewer
// Reseller Functions: Sales Manager, Operations, Viewer
// Internal Functions: Super Admin, Account Manager, Ad Ops, Support, Finance, Security

// Helper to determine default functions based on feature category
function getDefaultFunctions(hierarchy: string, featureName: string): string {
  const module = hierarchy.split('.')[0];
  const lowerName = featureName.toLowerCase();
  
  // Authentication - all users
  if (module === '1') return 'All';
  
  // Dashboard - varies by widget
  if (module === '2') {
    if (lowerName.includes('sales') || lowerName.includes('revenue')) {
      return 'Sales Rep, Media Buyer, Account Director, Sales Manager, Finance';
    }
    if (lowerName.includes('inventory') || lowerName.includes('utilization')) {
      return 'Operations Manager, Ad Ops, Sales Manager';
    }
    if (lowerName.includes('creative')) {
      return 'Creative Approver, Creative Manager, Operations Manager';
    }
    return 'All';
  }
  
  // Campaigns - create/edit vs view
  if (module === '3') {
    if (lowerName.includes('create') || lowerName.includes('new') || lowerName.includes('add')) {
      return 'Campaign Planner, Media Buyer, Account Director, Sales Rep, Ad Ops';
    }
    if (lowerName.includes('delete') || lowerName.includes('archive')) {
      return 'Account Director, Operations Manager, Super Admin';
    }
    if (lowerName.includes('approve')) {
      return 'Account Director, Operations Manager, Approver, Super Admin';
    }
    if (lowerName.includes('price') || lowerName.includes('cost') || lowerName.includes('budget')) {
      return 'Media Buyer, Account Director, Finance, Sales Manager';
    }
    return 'Campaign Planner, Media Buyer, Account Director, Sales Rep, Ad Ops, Viewer';
  }
  
  // Inventory - operations focused
  if (module === '4' || module === '5') {
    if (lowerName.includes('create') || lowerName.includes('add') || lowerName.includes('upload')) {
      return 'Operations Manager, Ad Ops';
    }
    if (lowerName.includes('rate') || lowerName.includes('price')) {
      return 'Operations Manager, Sales Rep, Sales Manager';
    }
    if (lowerName.includes('availability')) {
      return 'Operations Manager, Ad Ops, Campaign Planner';
    }
    return 'Operations Manager, Ad Ops, Sales Rep, Campaign Planner, Media Buyer';
  }
  
  // Proposals
  if (module === '6') {
    if (lowerName.includes('create') || lowerName.includes('generate')) {
      return 'Campaign Planner, Media Buyer, Account Director, Sales Rep';
    }
    if (lowerName.includes('approve') || lowerName.includes('accept')) {
      return 'Account Director, Approver, Brand Manager';
    }
    return 'Campaign Planner, Media Buyer, Account Director, Sales Rep, Viewer';
  }
  
  // Creatives
  if (module === '7') {
    if (lowerName.includes('upload') || lowerName.includes('create')) {
      return 'Creative Manager, Campaign Planner, Ad Ops';
    }
    if (lowerName.includes('approve') || lowerName.includes('reject')) {
      return 'Creative Approver, Operations Manager';
    }
    return 'Creative Manager, Creative Approver, Campaign Planner, Ad Ops';
  }
  
  // Reports
  if (module === '8') {
    if (lowerName.includes('financial') || lowerName.includes('revenue')) {
      return 'Finance, Account Director, Sales Manager';
    }
    return 'All';
  }
  
  // Settings/Admin
  if (module === '9' || module === '10') {
    if (lowerName.includes('user') || lowerName.includes('role')) {
      return 'Super Admin, Account Manager';
    }
    if (lowerName.includes('billing') || lowerName.includes('invoice')) {
      return 'Finance, Billing Admin, Super Admin';
    }
    return 'Super Admin, Account Manager, Operations Manager';
  }
  
  // Statements
  if (module === '11') {
    return 'Finance, Billing Admin, Account Director, Super Admin';
  }
  
  // Reservations
  if (module === '12') {
    if (lowerName.includes('accept') || lowerName.includes('decline')) {
      return 'Operations Manager, Sales Rep, Sales Manager';
    }
    return 'Campaign Planner, Media Buyer, Operations Manager, Sales Rep';
  }
  
  // Tags
  if (module === '13') {
    return 'All';
  }
  
  // Workbench
  if (module === '14') {
    return 'Campaign Planner, Media Buyer, Operations Manager, Ad Ops';
  }
  
  // Navigation
  if (module === '15') {
    return 'All';
  }
  
  // Map View
  if (module === '16') {
    return 'Campaign Planner, Media Buyer, Operations Manager, Ad Ops';
  }
  
  return 'All';
}

// Feature definitions without defaultFunctions - will be computed dynamically
interface FeatureBase {
  level: number;
  hierarchy: string;
  featureName: string;
  description: string;
  location: string;
  screenshotRef: string;
}

const featureData: FeatureBase[] = [
  // ==================== 1. AUTHENTICATION MODULE ====================
  { level: 0, hierarchy: "1", featureName: "Authentication", description: "User authentication module", location: "/auth", screenshotRef: "" },
  { level: 1, hierarchy: "1.1", featureName: "Login Page", description: "User login interface", location: "/auth", screenshotRef: "" },
  { level: 2, hierarchy: "1.1.1", featureName: "Login Form", description: "Credential entry form", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.1.1", featureName: "Username Input", description: "Username/email field", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.1.2", featureName: "Password Input", description: "Password field", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.1.3", featureName: "Remember Me Checkbox", description: "Stay logged in option", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.1.4", featureName: "Login Button", description: "Submit login credentials", location: "/auth", screenshotRef: "" },
  { level: 2, hierarchy: "1.1.2", featureName: "Alternative Login", description: "Other login methods", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.2.1", featureName: "Get Login Code Link", description: "Email code option", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.2.2", featureName: "Forgot Password Link", description: "Password reset", location: "/auth", screenshotRef: "" },
  { level: 2, hierarchy: "1.1.3", featureName: "Lead Form Section", description: "New user registration", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.3.1", featureName: "Company Name Input", description: "Company name field", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.3.2", featureName: "Contact Email Input", description: "Contact email field", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.3.3", featureName: "Business Type Select", description: "Business type dropdown", location: "/auth", screenshotRef: "" },
  { level: 3, hierarchy: "1.1.3.4", featureName: "Submit Lead Button", description: "Submit interest form", location: "/auth", screenshotRef: "" },

  // ==================== 2. DASHBOARD MODULE ====================
  { level: 0, hierarchy: "2", featureName: "Dashboard", description: "Main dashboard module", location: "/", screenshotRef: "" },
  { level: 1, hierarchy: "2.1", featureName: "Dashboard Header", description: "Top header section", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.1.1", featureName: "Welcome Message", description: "User greeting text", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.1.2", featureName: "Date Range Filter", description: "Dashboard date filter", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.1.2.1", featureName: "Start Date Picker", description: "Start date selection", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.1.2.2", featureName: "End Date Picker", description: "End date selection", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.1.2.3", featureName: "Apply Filter Button", description: "Apply date filter", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.1.3", featureName: "Tenant Indicator Badge", description: "Current company name", location: "/", screenshotRef: "" },
  
  { level: 1, hierarchy: "2.2", featureName: "Quick Actions Bar", description: "Primary action buttons", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.2.1", featureName: "Create Campaign Button", description: "New campaign action", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.2.2", featureName: "Create Proposal Button", description: "New proposal action", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.2.3", featureName: "View Reports Button", description: "Open reports", location: "/", screenshotRef: "" },
  
  { level: 1, hierarchy: "2.3", featureName: "Campaign Overview Widget", description: "Campaign summary stats", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.3.1", featureName: "Active Campaigns Count", description: "Number of active campaigns", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.3.2", featureName: "Pending Campaigns Count", description: "Pending campaign count", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.3.3", featureName: "Completed Campaigns Count", description: "Completed campaign count", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.3.4", featureName: "View All Link", description: "Go to campaigns list", location: "/", screenshotRef: "" },
  
  { level: 1, hierarchy: "2.4", featureName: "Sales Performance Widget", description: "Revenue metrics", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.4.1", featureName: "Total Revenue Display", description: "Revenue amount", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.4.2", featureName: "Revenue Trend Chart", description: "Revenue over time", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.4.3", featureName: "Period Comparison", description: "vs previous period", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.4.4", featureName: "Regional Breakdown", description: "Revenue by region", location: "/", screenshotRef: "" },
  
  { level: 1, hierarchy: "2.5", featureName: "Inventory Utilization Widget", description: "Inventory usage stats", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.5.1", featureName: "View Toggle (Type/Format)", description: "Switch breakdown view", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.5.2", featureName: "Utilization Bar Chart", description: "Usage by type/format", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.5.3", featureName: "Available Count", description: "Available inventory", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.5.4", featureName: "Booked Count", description: "Booked inventory", location: "/", screenshotRef: "" },
  
  { level: 1, hierarchy: "2.6", featureName: "Creative Status Widget", description: "Creative approval stats", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.6.1", featureName: "Pending Approval Count", description: "Awaiting approval", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.6.2", featureName: "Approved Count", description: "Approved creatives", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.6.3", featureName: "Rejected Count", description: "Rejected creatives", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.6.4", featureName: "View Details Link", description: "Go to creatives", location: "/", screenshotRef: "" },
  
  { level: 1, hierarchy: "2.7", featureName: "Recent Activity Widget", description: "Activity feed", location: "/", screenshotRef: "" },
  { level: 2, hierarchy: "2.7.1", featureName: "Activity Item", description: "Single activity entry", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.7.1.1", featureName: "Activity Icon", description: "Activity type icon", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.7.1.2", featureName: "Activity Description", description: "What happened", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.7.1.3", featureName: "Activity Timestamp", description: "When it happened", location: "/", screenshotRef: "" },
  { level: 3, hierarchy: "2.7.1.4", featureName: "View Action Link", description: "Go to item", location: "/", screenshotRef: "" },

  // ==================== 3. CAMPAIGNS MODULE ====================
  { level: 0, hierarchy: "3", featureName: "Campaigns", description: "Campaign management module", location: "/campaigns", screenshotRef: "" },
  
  { level: 1, hierarchy: "3.1", featureName: "Campaigns List Page", description: "Campaign listing view", location: "/campaigns", screenshotRef: "" },
  { level: 2, hierarchy: "3.1.1", featureName: "Page Header", description: "Page title area", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.1.1", featureName: "Page Title", description: "Campaigns heading", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.1.2", featureName: "Create Campaign Button", description: "New campaign action", location: "/campaigns", screenshotRef: "" },
  { level: 2, hierarchy: "3.1.2", featureName: "Search & Filter Bar", description: "Campaign filtering", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.2.1", featureName: "Search Input", description: "Search campaigns", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.2.2", featureName: "Status Filter Tabs", description: "Filter by status", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.2.3", featureName: "Country Filter", description: "Filter by country", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.2.4", featureName: "Date Range Filter", description: "Filter by dates", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.2.5", featureName: "View Toggle (List/Grid)", description: "Switch view mode", location: "/campaigns", screenshotRef: "" },
  { level: 2, hierarchy: "3.1.3", featureName: "Campaign Table", description: "Campaigns data table", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.3.1", featureName: "Select All Checkbox", description: "Bulk select", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.3.2", featureName: "Campaign Name Column", description: "Campaign name", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.3.3", featureName: "Status Column", description: "Campaign status", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.3.4", featureName: "Dates Column", description: "Start/end dates", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.3.5", featureName: "Budget Column", description: "Campaign budget", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.3.6", featureName: "Actions Column", description: "Row actions menu", location: "/campaigns", screenshotRef: "" },
  { level: 2, hierarchy: "3.1.4", featureName: "Row Actions Menu", description: "Per-row actions", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.4.1", featureName: "View Details", description: "Open campaign", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.4.2", featureName: "Edit Campaign", description: "Edit campaign", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.4.3", featureName: "Duplicate Campaign", description: "Clone campaign", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.4.4", featureName: "Manage Approval", description: "Open approval", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.4.5", featureName: "Delete Campaign", description: "Remove campaign", location: "/campaigns", screenshotRef: "" },
  { level: 2, hierarchy: "3.1.5", featureName: "Pagination", description: "Page navigation", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.5.1", featureName: "Previous Page Button", description: "Go to previous", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.5.2", featureName: "Page Number Buttons", description: "Jump to page", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.5.3", featureName: "Next Page Button", description: "Go to next", location: "/campaigns", screenshotRef: "" },
  { level: 3, hierarchy: "3.1.5.4", featureName: "Items Per Page Select", description: "Rows per page", location: "/campaigns", screenshotRef: "" },

  // ==================== 3.2 CREATE CAMPAIGN ====================
  { level: 1, hierarchy: "3.2", featureName: "Create Campaign", description: "Campaign creation wizard", location: "/new-campaign", screenshotRef: "" },
  { level: 2, hierarchy: "3.2.1", featureName: "Step Navigation", description: "Wizard step indicators", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.1.1", featureName: "Step 1 Badge (Campaign Details)", description: "First step indicator", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.1.2", featureName: "Step 2 Badge (Budget & Location)", description: "Second step indicator", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.1.3", featureName: "Step 3 Badge (Targeting)", description: "Third step indicator", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.1.4", featureName: "Step 4 Badge (Inventories)", description: "Fourth step indicator", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.1.5", featureName: "Step 5 Badge (Optimization)", description: "Fifth step indicator", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.1.6", featureName: "Progress Line", description: "Step completion line", location: "/new-campaign", screenshotRef: "" },
  
  // Step 1: Campaign Details
  { level: 2, hierarchy: "3.2.2", featureName: "Step 1 - Campaign Details", description: "Basic campaign info", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.1", featureName: "Campaign Name Input", description: "Enter campaign name", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.2", featureName: "External ID Input", description: "Optional external ref", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.3", featureName: "Campaign Dates Picker", description: "Start and end dates", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.4", featureName: "Client Type Select", description: "Direct or Agency", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.5", featureName: "Agency Selector", description: "Select agency (if agency)", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.6", featureName: "Create New Agency Button", description: "Quick agency creation", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.7", featureName: "Brand Selector", description: "Select brand", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.8", featureName: "Create New Brand Button", description: "Quick brand creation", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.9", featureName: "Form Insights Panel", description: "Right side tips", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.2.10", featureName: "Quick Tips Panel", description: "Execution plan tips", location: "/new-campaign", screenshotRef: "" },
  
  // Step 2: Budget & Location
  { level: 2, hierarchy: "3.2.3", featureName: "Step 2 - Budget & Location", description: "Budget and geo settings", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.1", featureName: "Country Selector", description: "Select target country", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.2", featureName: "Currency Display", description: "Auto-set currency", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.3", featureName: "Budget Input", description: "Enter campaign budget", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.4", featureName: "Goal Type Selector", description: "Impressions/Reach/SOV", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.5", featureName: "Goal Target Value Input", description: "Enter goal target", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.6", featureName: "Custom Goal Name Input", description: "For custom goals", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.7", featureName: "Market Insights Card", description: "Country stats panel", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.8", featureName: "Country Map Display", description: "Country shape image", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.9", featureName: "Population Display", description: "Market population", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.10", featureName: "Inventories Count Display", description: "Available inventory", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.3.11", featureName: "OOH Impressions Display", description: "Market impressions", location: "/new-campaign", screenshotRef: "" },
  
  // Step 3: Targeting
  { level: 2, hierarchy: "3.2.4", featureName: "Step 3 - Targeting", description: "Audience targeting", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.1", featureName: "Apply Recommendations Button", description: "Use AI suggestions", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.2", featureName: "Demographics Tab", description: "Demo targeting tab", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.3", featureName: "Age Groups Multi-Select", description: "Select age ranges", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.4", featureName: "Gender Multi-Select", description: "Select genders", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.5", featureName: "Income Brackets Multi-Select", description: "Select income levels", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.6", featureName: "Interests Multi-Select", description: "Select interests", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.7", featureName: "Venue Types Selector", description: "OpenOOH venues", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.8", featureName: "Audience Behavior Multi-Select", description: "Behavior targeting", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.9", featureName: "Geofencing Tab", description: "Geo targeting tab", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.10", featureName: "Geo Targets List", description: "Location targets", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.11", featureName: "Add Location Button", description: "Add geo target", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.12", featureName: "Signals Tab", description: "Signal triggers tab", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.13", featureName: "Signals Multi-Select", description: "Select signals", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.4.14", featureName: "Add New Signal Button", description: "Create signal", location: "/new-campaign", screenshotRef: "" },
  
  // Step 4: Inventories/Media Selection
  { level: 2, hierarchy: "3.2.5", featureName: "Step 4 - Inventories", description: "Inventory selection", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.1", featureName: "Search Input", description: "Search inventories", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.2", featureName: "Filters Button", description: "Open filter panel", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.3", featureName: "Location Filter", description: "Filter by location", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.4", featureName: "Media Owner Filter", description: "Filter by owner", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.5", featureName: "Venue Type Filter", description: "Filter by venue", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.6", featureName: "Inventory Type Filter", description: "Classic/Digital etc", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.7", featureName: "CPM Range Filter", description: "Min/Max CPM", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.8", featureName: "Upload CSV Button", description: "Bulk import", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.9", featureName: "Download Template Button", description: "Get CSV template", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.10", featureName: "View Map Button", description: "Open map view", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.11", featureName: "View Availability Button", description: "Open calendar", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.12", featureName: "Inventory Table", description: "Inventory list", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.13", featureName: "Select All Checkbox", description: "Select all items", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.14", featureName: "Inventory Row Checkbox", description: "Select single item", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.15", featureName: "Inventory Name Cell", description: "Inventory name", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.16", featureName: "Type Badge Cell", description: "Inventory type", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.17", featureName: "Location Cell", description: "Address/City", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.18", featureName: "Media Owner Cell", description: "Owner name", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.19", featureName: "CPM Cell", description: "Cost per mille", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.20", featureName: "Impressions Cell", description: "Monthly impressions", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.21", featureName: "SOV Slider", description: "Share of voice", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.22", featureName: "View Details Button", description: "Open side panel", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.23", featureName: "View on Map Button", description: "Show on map", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.24", featureName: "Inventory Side Panel", description: "Detail drawer", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.25", featureName: "Selection Summary Card", description: "Selected stats", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.26", featureName: "Total Cost Display", description: "Sum of costs", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.27", featureName: "Total Impressions Display", description: "Sum of impressions", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.5.28", featureName: "Average CPM Display", description: "Avg CPM", location: "/new-campaign", screenshotRef: "" },
  
  // Step 5: Optimization
  { level: 2, hierarchy: "3.2.6", featureName: "Step 5 - Optimization", description: "Campaign optimization", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.1", featureName: "Budget Allocation Tab", description: "Budget tab", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.2", featureName: "Budget Pie Chart", description: "Visual allocation", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.3", featureName: "Digital Budget Slider", description: "Digital %", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.4", featureName: "Classic Budget Slider", description: "Classic %", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.5", featureName: "Transit Budget Slider", description: "Transit %", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.6", featureName: "Retail Budget Slider", description: "Retail %", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.7", featureName: "Schedule Tab", description: "Scheduling tab", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.8", featureName: "Schedule Preset Selector", description: "Commuter/Weekend etc", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.9", featureName: "Weekday Weight Sliders", description: "Per-day weights", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.10", featureName: "Time of Day Weight Sliders", description: "Per-slot weights", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.11", featureName: "Inventory Schedule Grid", description: "24x7 hour grid", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.12", featureName: "Grid Hour Cell", description: "Click to toggle hour", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.13", featureName: "Grid Row Header (Day)", description: "Day of week label", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.14", featureName: "Grid Column Header (Hour)", description: "Hour label", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.15", featureName: "Preset Pattern Button", description: "Apply preset pattern", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.16", featureName: "Clear Grid Button", description: "Reset grid", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.17", featureName: "Schedule Metrics Display", description: "Calculated metrics", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.18", featureName: "Auto-Optimize Tab", description: "AI optimization", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.19", featureName: "Auto-Optimize Toggle", description: "Enable/Disable", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.20", featureName: "Run Optimization Button", description: "Start optimization", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.21", featureName: "Reset Optimization Button", description: "Clear optimizations", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.22", featureName: "Reach Curve Chart", description: "Budget vs reach", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.6.23", featureName: "Forecasting Panel", description: "Estimated metrics", location: "/new-campaign", screenshotRef: "" },
  
  // Footer Navigation
  { level: 2, hierarchy: "3.2.7", featureName: "Wizard Footer", description: "Navigation buttons", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.7.1", featureName: "Cancel Button", description: "Cancel and exit", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.7.2", featureName: "Previous Button", description: "Go to previous step", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.7.3", featureName: "Next Button", description: "Go to next step", location: "/new-campaign", screenshotRef: "" },
  { level: 3, hierarchy: "3.2.7.4", featureName: "Finalize Campaign Button", description: "Complete wizard", location: "/new-campaign", screenshotRef: "" },

  // ==================== 3.3 CAMPAIGN DETAIL ====================
  { level: 1, hierarchy: "3.3", featureName: "Campaign Detail Page", description: "View campaign details", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.1", featureName: "Campaign Header", description: "Top info section", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.1.1", featureName: "Back Button", description: "Return to list", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.1.2", featureName: "Campaign Name", description: "Campaign title", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.1.3", featureName: "Status Badge", description: "Current status", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.1.4", featureName: "Execution Path Badge", description: "Quick/Full workflow", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.1.5", featureName: "Actions Dropdown", description: "Campaign actions", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.2", featureName: "Summary Cards Row", description: "Key metrics cards", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.2.1", featureName: "Budget Card", description: "Budget and spent", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.2.2", featureName: "Impressions Card", description: "Impression metrics", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.2.3", featureName: "Reach Card", description: "Reach metrics", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.2.4", featureName: "Frequency Card", description: "Frequency metrics", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.3", featureName: "Tab Navigation", description: "Detail section tabs", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.3.1", featureName: "Overview Tab", description: "General info tab", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.3.2", featureName: "Inventories Tab", description: "Inventory list tab", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.3.3", featureName: "Schedule Tab", description: "Schedule view tab", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.3.4", featureName: "Creatives Tab", description: "Assigned creatives", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.3.5", featureName: "History Tab", description: "Audit log tab", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.4", featureName: "Overview Section", description: "Campaign overview", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.4.1", featureName: "Campaign Description", description: "Description text", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.4.2", featureName: "Date Range Display", description: "Start/End dates", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.4.3", featureName: "Brand Display", description: "Selected brand", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.4.4", featureName: "Agency Display", description: "Selected agency", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.4.5", featureName: "Location Display", description: "Target country", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.4.6", featureName: "Goal Display", description: "Campaign goal", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.5", featureName: "Inventories Section", description: "Campaign inventories", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.5.1", featureName: "Breakdown Selector", description: "Group by type/city", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.5.2", featureName: "Inventory Accordion", description: "Expandable groups", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.5.3", featureName: "Inventory Row", description: "Single inventory", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.5.4", featureName: "View Details Link", description: "Open side panel", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.6", featureName: "Schedule Section", description: "Campaign schedule", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.6.1", featureName: "Schedule Entry Card", description: "Schedule item", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.6.2", featureName: "Schedule Grid Display", description: "24x7 grid (read-only)", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.6.3", featureName: "Schedule Metrics", description: "Calculated hours/plays", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.7", featureName: "Creatives Section", description: "Assigned creatives", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.7.1", featureName: "Creative Card", description: "Single creative", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.7.2", featureName: "Assign Creative Button", description: "Add creative", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.7.3", featureName: "Remove Creative Button", description: "Unassign creative", location: "/campaigns/:id", screenshotRef: "" },
  { level: 2, hierarchy: "3.3.8", featureName: "History Section", description: "Campaign history", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.8.1", featureName: "History Entry", description: "Single log entry", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.8.2", featureName: "Entry Timestamp", description: "When happened", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.8.3", featureName: "Entry User", description: "Who did it", location: "/campaigns/:id", screenshotRef: "" },
  { level: 3, hierarchy: "3.3.8.4", featureName: "Entry Action", description: "What happened", location: "/campaigns/:id", screenshotRef: "" },

  // ==================== 4. INVENTORIES MODULE ====================
  { level: 0, hierarchy: "4", featureName: "Inventories", description: "Inventory management module", location: "/inventories", screenshotRef: "" },
  { level: 1, hierarchy: "4.1", featureName: "Inventories List Page", description: "Inventory listing", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.1.1", featureName: "Page Header", description: "Page title area", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.1.1", featureName: "Page Title", description: "Inventories heading", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.1.2", featureName: "Add Inventory Button", description: "Create inventory", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.1.2", featureName: "Tab Navigation", description: "Traditional/Programmatic", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.2.1", featureName: "Traditional Tab", description: "Traditional inventory", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.2.2", featureName: "Programmatic Tab", description: "Programmatic inventory", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.1.3", featureName: "Filter Bar", description: "Inventory filtering", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.3.1", featureName: "Search Input", description: "Search inventories", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.3.2", featureName: "Type Filter", description: "Digital/Classic etc", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.3.3", featureName: "Format Filter", description: "Size/Format", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.3.4", featureName: "Status Filter", description: "Active/Inactive", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.3.5", featureName: "View Toggle (List/Map)", description: "Switch view mode", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.1.4", featureName: "Inventory Table", description: "Inventory data table", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.1", featureName: "Select All Checkbox", description: "Bulk select", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.2", featureName: "Name Column", description: "Inventory name", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.3", featureName: "Type Column", description: "Inventory type", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.4", featureName: "Location Column", description: "Address/City", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.5", featureName: "Rate Card Column", description: "Pricing", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.6", featureName: "Status Column", description: "Active status", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.4.7", featureName: "Actions Column", description: "Row actions", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.1.5", featureName: "Row Actions Menu", description: "Per-row actions", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.5.1", featureName: "View Details", description: "Open detail", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.5.2", featureName: "Edit Inventory", description: "Edit inventory", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.5.3", featureName: "View Availability", description: "Open calendar", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.1.5.4", featureName: "Delete Inventory", description: "Remove inventory", location: "/inventories", screenshotRef: "" },
  
  { level: 1, hierarchy: "4.2", featureName: "Inventory Side Panel", description: "Inventory detail drawer", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.2.1", featureName: "Panel Header", description: "Title and close", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.1.1", featureName: "Inventory Name", description: "Panel title", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.1.2", featureName: "Close Button", description: "Close panel", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.2.2", featureName: "Overview Tab", description: "General info", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.2.1", featureName: "Image Gallery", description: "Inventory photos", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.2.2", featureName: "Type Badge", description: "Inventory type", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.2.3", featureName: "Format Display", description: "Size/Format", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.2.4", featureName: "Address Display", description: "Full address", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.2.5", featureName: "Media Owner Display", description: "Owner name", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.2.3", featureName: "Performance Tab", description: "Performance metrics", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.3.1", featureName: "Impressions Display", description: "Monthly impressions", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.3.2", featureName: "Reach Display", description: "Monthly reach", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.3.3", featureName: "Frequency Display", description: "Avg frequency", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.3.4", featureName: "eCPM Display", description: "Effective CPM", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.3.5", featureName: "Demographics Chart", description: "Age/Gender breakdown", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.2.4", featureName: "Availability Tab", description: "Calendar view", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.4.1", featureName: "Month Navigator", description: "Change month", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.4.2", featureName: "Calendar Grid", description: "Availability calendar", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.4.3", featureName: "Available Day Cell", description: "Green available", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.4.4", featureName: "Booked Day Cell", description: "Red booked", location: "/inventories", screenshotRef: "" },
  { level: 2, hierarchy: "4.2.5", featureName: "Pricing Tab", description: "Rate card info", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.5.1", featureName: "Rate Card Display", description: "Standard pricing", location: "/inventories", screenshotRef: "" },
  { level: 3, hierarchy: "4.2.5.2", featureName: "Discounts Display", description: "Volume discounts", location: "/inventories", screenshotRef: "" },

  // ==================== 5. CREATIVES MODULE ====================
  { level: 0, hierarchy: "5", featureName: "Creatives", description: "Creative management module", location: "/creatives", screenshotRef: "" },
  { level: 1, hierarchy: "5.1", featureName: "Creatives List Page", description: "Creative library", location: "/creatives", screenshotRef: "" },
  { level: 2, hierarchy: "5.1.1", featureName: "Page Header", description: "Page title area", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.1.1", featureName: "Page Title", description: "Creatives heading", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.1.2", featureName: "Upload Creative Button", description: "Add creative", location: "/creatives", screenshotRef: "" },
  { level: 2, hierarchy: "5.1.2", featureName: "Filter Bar", description: "Creative filtering", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.2.1", featureName: "Search Input", description: "Search creatives", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.2.2", featureName: "Type Filter", description: "Image/Video etc", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.2.3", featureName: "Status Filter", description: "Approval status", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.2.4", featureName: "Folder Filter", description: "Filter by folder", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.2.5", featureName: "View Toggle (Grid/List)", description: "Switch view mode", location: "/creatives", screenshotRef: "" },
  { level: 2, hierarchy: "5.1.3", featureName: "Creative Grid", description: "Creative cards", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.3.1", featureName: "Creative Card", description: "Single creative", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.3.2", featureName: "Thumbnail Image", description: "Creative preview", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.3.3", featureName: "Creative Name", description: "File name", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.3.4", featureName: "Format Badge", description: "Image/Video type", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.3.5", featureName: "Status Badge", description: "Approval status", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.3.6", featureName: "Actions Menu", description: "Card actions", location: "/creatives", screenshotRef: "" },
  { level: 2, hierarchy: "5.1.4", featureName: "Upload Dialog", description: "File upload modal", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.1", featureName: "Dropzone Area", description: "Drag and drop zone", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.2", featureName: "Browse Files Button", description: "File picker", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.3", featureName: "File Preview", description: "Selected file preview", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.4", featureName: "Creative Name Input", description: "Enter name", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.5", featureName: "Folder Select", description: "Choose folder", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.6", featureName: "Upload Button", description: "Start upload", location: "/creatives", screenshotRef: "" },
  { level: 3, hierarchy: "5.1.4.7", featureName: "Cancel Button", description: "Cancel upload", location: "/creatives", screenshotRef: "" },

  // ==================== 6. PROPOSALS MODULE ====================
  { level: 0, hierarchy: "6", featureName: "Proposals", description: "Proposal management module", location: "/proposals", screenshotRef: "" },
  { level: 1, hierarchy: "6.1", featureName: "Proposals List Page", description: "Proposal listing", location: "/proposals", screenshotRef: "" },
  { level: 2, hierarchy: "6.1.1", featureName: "Page Header", description: "Page title area", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.1.1", featureName: "Page Title", description: "Proposals heading", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.1.2", featureName: "Create Proposal Button", description: "New proposal", location: "/proposals", screenshotRef: "" },
  { level: 2, hierarchy: "6.1.2", featureName: "Filter Bar", description: "Proposal filtering", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.2.1", featureName: "Search Input", description: "Search proposals", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.2.2", featureName: "Status Filter Tabs", description: "Filter by status", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.2.3", featureName: "Date Range Filter", description: "Filter by dates", location: "/proposals", screenshotRef: "" },
  { level: 2, hierarchy: "6.1.3", featureName: "Proposals Table", description: "Proposals data table", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.3.1", featureName: "Proposal Name Column", description: "Proposal name", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.3.2", featureName: "Client Column", description: "Client name", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.3.3", featureName: "Value Column", description: "Proposal value", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.3.4", featureName: "Status Column", description: "Proposal status", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.3.5", featureName: "Actions Column", description: "Row actions", location: "/proposals", screenshotRef: "" },
  { level: 2, hierarchy: "6.1.4", featureName: "Row Actions Menu", description: "Per-row actions", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.4.1", featureName: "View Details", description: "Open proposal", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.4.2", featureName: "Edit Proposal", description: "Edit proposal", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.4.3", featureName: "Convert to Campaign", description: "Create campaign", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.4.4", featureName: "Duplicate Proposal", description: "Clone proposal", location: "/proposals", screenshotRef: "" },
  { level: 3, hierarchy: "6.1.4.5", featureName: "Delete Proposal", description: "Remove proposal", location: "/proposals", screenshotRef: "" },

  // ==================== 7. STATEMENTS MODULE ====================
  { level: 0, hierarchy: "7", featureName: "Statements", description: "Statement management module", location: "/statements", screenshotRef: "" },
  { level: 1, hierarchy: "7.1", featureName: "Statements List Page", description: "Statement listing", location: "/statements", screenshotRef: "" },
  { level: 2, hierarchy: "7.1.1", featureName: "Page Header", description: "Page title area", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.1.1", featureName: "Page Title", description: "Statements heading", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.1.2", featureName: "Create Statement Button", description: "New statement", location: "/statements", screenshotRef: "" },
  { level: 2, hierarchy: "7.1.2", featureName: "Summary Cards", description: "Financial summary", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.2.1", featureName: "Total Revenue Card", description: "Total revenue", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.2.2", featureName: "Pending Card", description: "Pending amount", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.2.3", featureName: "Paid Card", description: "Paid amount", location: "/statements", screenshotRef: "" },
  { level: 2, hierarchy: "7.1.3", featureName: "Filter Bar", description: "Statement filtering", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.3.1", featureName: "Search Input", description: "Search statements", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.3.2", featureName: "Status Filter", description: "Payment status", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.3.3", featureName: "Date Range Filter", description: "Filter by dates", location: "/statements", screenshotRef: "" },
  { level: 2, hierarchy: "7.1.4", featureName: "Statements Table", description: "Statements data table", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.4.1", featureName: "Statement ID Column", description: "Statement number", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.4.2", featureName: "Campaign Column", description: "Related campaign", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.4.3", featureName: "Amount Column", description: "Statement amount", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.4.4", featureName: "Status Column", description: "Payment status", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.4.5", featureName: "Actions Column", description: "Row actions", location: "/statements", screenshotRef: "" },
  { level: 2, hierarchy: "7.1.5", featureName: "Row Actions Menu", description: "Per-row actions", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.5.1", featureName: "View Details", description: "Open statement", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.5.2", featureName: "Download PDF", description: "Export PDF", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.5.3", featureName: "Split Statement", description: "Split action", location: "/statements", screenshotRef: "" },
  { level: 3, hierarchy: "7.1.5.4", featureName: "Mark as Paid", description: "Update status", location: "/statements", screenshotRef: "" },

  // ==================== 8. TAGS MODULE ====================
  { level: 0, hierarchy: "8", featureName: "Tags", description: "Tag management module", location: "/tags", screenshotRef: "" },
  { level: 1, hierarchy: "8.1", featureName: "Tags Management Page", description: "Tag configuration", location: "/tags", screenshotRef: "" },
  { level: 2, hierarchy: "8.1.1", featureName: "Page Header", description: "Page title area", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.1.1", featureName: "Page Title", description: "Tags heading", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.1.2", featureName: "Create Tag Button", description: "New tag", location: "/tags", screenshotRef: "" },
  { level: 2, hierarchy: "8.1.2", featureName: "Filter Bar", description: "Tag filtering", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.2.1", featureName: "Search Input", description: "Search tags", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.2.2", featureName: "Category Filter", description: "Filter by category", location: "/tags", screenshotRef: "" },
  { level: 2, hierarchy: "8.1.3", featureName: "Tags Table", description: "Tags data table", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.3.1", featureName: "Tag Name Column", description: "Tag name", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.3.2", featureName: "Category Column", description: "Tag category", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.3.3", featureName: "Color Column", description: "Tag color", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.3.4", featureName: "Usage Count Column", description: "Times used", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.3.5", featureName: "Actions Column", description: "Row actions", location: "/tags", screenshotRef: "" },
  { level: 2, hierarchy: "8.1.4", featureName: "Create Tag Dialog", description: "Add tag modal", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.4.1", featureName: "Tag Name Input", description: "Enter name", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.4.2", featureName: "Category Select", description: "Choose category", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.4.3", featureName: "Color Picker", description: "Select color", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.4.4", featureName: "Create Button", description: "Save tag", location: "/tags", screenshotRef: "" },
  { level: 3, hierarchy: "8.1.4.5", featureName: "Cancel Button", description: "Close dialog", location: "/tags", screenshotRef: "" },

  // ==================== 9. PRICE MANAGEMENT MODULE ====================
  { level: 0, hierarchy: "9", featureName: "Price Management", description: "Price negotiation module", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 1, hierarchy: "9.1", featureName: "Price Management Page", description: "Pricing interface", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.1.1", featureName: "Page Header", description: "Page title area", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.1.1", featureName: "Back Button", description: "Return to campaign", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.1.2", featureName: "Page Title", description: "Price Management heading", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.1.3", featureName: "Campaign Name", description: "Current campaign", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.1.2", featureName: "Tips Carousel", description: "Feature tips slider", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.2.1", featureName: "Tip Card", description: "Individual tip", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.2.2", featureName: "Previous Tip Button", description: "Show previous", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.2.3", featureName: "Next Tip Button", description: "Show next", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.1.3", featureName: "Selection Action Bar", description: "Bulk actions bar", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.3.1", featureName: "Selected Count Display", description: "Items selected", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.3.2", featureName: "Accept Price Button", description: "Accept selected", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.3.3", featureName: "Clear Selection Button", description: "Deselect all", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.3.4", featureName: "Apply Discount Button", description: "Bulk discount", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.3.5", featureName: "Apply Bonus Button", description: "Bulk bonus", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.3.6", featureName: "Change SOV Button", description: "Bulk SOV change", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.1.4", featureName: "Pricing Table", description: "Price data table", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.1", featureName: "Select All Checkbox", description: "Select all rows", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.2", featureName: "Inventory Name Column", description: "Inventory name", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.3", featureName: "Rate Card Column", description: "Standard price", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.4", featureName: "Proposed Price Column", description: "Current offer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.5", featureName: "Status Column", description: "Negotiation status", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.6", featureName: "Proposer Column", description: "Who proposed", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.4.7", featureName: "Actions Column", description: "Row actions", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.1.5", featureName: "Status Legend", description: "Status color codes", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.5.1", featureName: "Rate Card Legend", description: "Standard rate", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.5.2", featureName: "Proposed Legend", description: "Proposed offer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.5.3", featureName: "Counter Legend", description: "Counter offer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.5.4", featureName: "Accepted Legend", description: "Accepted price", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.1.5.5", featureName: "Declined Legend", description: "Declined offer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  
  { level: 1, hierarchy: "9.2", featureName: "Negotiation Workspace", description: "Negotiation drawer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.2.1", featureName: "Drawer Header", description: "Title and close", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.1.1", featureName: "Drawer Title", description: "Negotiation heading", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.1.2", featureName: "Close Button", description: "Close drawer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.1.3", featureName: "New Request Button", description: "Start negotiation", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.2.2", featureName: "Thread List", description: "Negotiation threads", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.2.1", featureName: "Thread Card", description: "Single thread", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.2.2", featureName: "Thread Status Badge", description: "Thread status", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.2.3", featureName: "Thread Click", description: "Open thread", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.2.3", featureName: "Message Timeline", description: "Thread messages", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.3.1", featureName: "Message Entry", description: "Single message", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.3.2", featureName: "Sender Info", description: "Who sent", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.3.3", featureName: "Timestamp", description: "When sent", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.3.4", featureName: "Message Content", description: "Message text", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.2.4", featureName: "Counter Offer Form", description: "Make counter offer", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.4.1", featureName: "New Price Input", description: "Counter price", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.4.2", featureName: "Discount Input", description: "Discount %", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.4.3", featureName: "SOV Input", description: "SOV %", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.4.4", featureName: "Message Input", description: "Notes field", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.4.5", featureName: "Submit Counter Button", description: "Send counter", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 2, hierarchy: "9.2.5", featureName: "Negotiation Actions", description: "Action buttons", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.5.1", featureName: "Accept Offer Button", description: "Accept current", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.5.2", featureName: "Decline Offer Button", description: "Decline current", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.5.3", featureName: "Make Counter Button", description: "Open counter form", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.5.4", featureName: "Escalate to Finance Button", description: "Finance escalation", location: "/campaigns/:id/price-management", screenshotRef: "" },
  { level: 3, hierarchy: "9.2.5.5", featureName: "Back to List Button", description: "Return to threads", location: "/campaigns/:id/price-management", screenshotRef: "" },

  // ==================== 10. RESERVATIONS MODULE ====================
  { level: 0, hierarchy: "10", featureName: "Reservations", description: "Inventory reservation module", location: "/reservations", screenshotRef: "" },
  { level: 1, hierarchy: "10.1", featureName: "Reservations Page", description: "Reservation management", location: "/reservations", screenshotRef: "" },
  { level: 2, hierarchy: "10.1.1", featureName: "Page Header", description: "Page title area", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.1.1", featureName: "Page Title", description: "Reservations heading", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.1.2", featureName: "Create Hold Button", description: "New hold request", location: "/reservations", screenshotRef: "" },
  { level: 2, hierarchy: "10.1.2", featureName: "Filter Bar", description: "Reservation filtering", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.2.1", featureName: "Search Input", description: "Search reservations", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.2.2", featureName: "Status Filter", description: "Hold/Confirmed", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.2.3", featureName: "Expiry Filter", description: "Expiring soon", location: "/reservations", screenshotRef: "" },
  { level: 2, hierarchy: "10.1.3", featureName: "Reservations Table", description: "Reservations data table", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.3.1", featureName: "Inventory Name Column", description: "Reserved inventory", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.3.2", featureName: "Campaign Column", description: "Related campaign", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.3.3", featureName: "Status Column", description: "Hold/Confirmed", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.3.4", featureName: "Expiry Column", description: "Expiry date", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.3.5", featureName: "Countdown Timer", description: "Time remaining", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.3.6", featureName: "Actions Column", description: "Row actions", location: "/reservations", screenshotRef: "" },
  { level: 2, hierarchy: "10.1.4", featureName: "Row Actions Menu", description: "Per-row actions", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.4.1", featureName: "View Details", description: "Open detail", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.4.2", featureName: "Extend Hold Button", description: "Request extension", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.4.3", featureName: "Accept Hold Button", description: "Confirm (MO only)", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.4.4", featureName: "Decline Hold Button", description: "Reject (MO only)", location: "/reservations", screenshotRef: "" },
  { level: 3, hierarchy: "10.1.4.5", featureName: "Release Hold Button", description: "Cancel hold", location: "/reservations", screenshotRef: "" },

  // ==================== 11. PROFILE MODULE ====================
  { level: 0, hierarchy: "11", featureName: "Profile", description: "User profile module", location: "/profile", screenshotRef: "" },
  { level: 1, hierarchy: "11.1", featureName: "Profile Page", description: "User settings", location: "/profile", screenshotRef: "" },
  { level: 2, hierarchy: "11.1.1", featureName: "Profile Header", description: "User info section", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.1.1", featureName: "Avatar Display", description: "User avatar", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.1.2", featureName: "User Name Display", description: "Full name", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.1.3", featureName: "Email Display", description: "Email address", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.1.4", featureName: "Role Display", description: "User role", location: "/profile", screenshotRef: "" },
  { level: 2, hierarchy: "11.1.2", featureName: "Personal Info Tab", description: "Personal details", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.2.1", featureName: "First Name Input", description: "First name field", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.2.2", featureName: "Last Name Input", description: "Last name field", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.2.3", featureName: "Email Input", description: "Email field", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.2.4", featureName: "Phone Input", description: "Phone field", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.2.5", featureName: "Save Changes Button", description: "Update profile", location: "/profile", screenshotRef: "" },
  { level: 2, hierarchy: "11.1.3", featureName: "Security Tab", description: "Password settings", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.3.1", featureName: "Current Password Input", description: "Current password", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.3.2", featureName: "New Password Input", description: "New password", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.3.3", featureName: "Confirm Password Input", description: "Confirm password", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.3.4", featureName: "Update Password Button", description: "Change password", location: "/profile", screenshotRef: "" },
  { level: 2, hierarchy: "11.1.4", featureName: "Notifications Tab", description: "Notification prefs", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.4.1", featureName: "Email Notifications Toggle", description: "Email alerts", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.4.2", featureName: "Campaign Updates Toggle", description: "Campaign alerts", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.4.3", featureName: "Approval Requests Toggle", description: "Approval alerts", location: "/profile", screenshotRef: "" },
  { level: 3, hierarchy: "11.1.4.4", featureName: "Save Preferences Button", description: "Save settings", location: "/profile", screenshotRef: "" },

  // ==================== 12. GLOBAL NAVIGATION ====================
  { level: 0, hierarchy: "12", featureName: "Global Navigation", description: "App-wide navigation", location: "Global", screenshotRef: "" },
  { level: 1, hierarchy: "12.1", featureName: "Top Navigation Bar", description: "Header navigation", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.1.1", featureName: "Logo/Home Link", description: "App logo", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.1.2", featureName: "Company Switcher", description: "Tenant selector", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.2.1", featureName: "Current Company Display", description: "Selected company", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.2.2", featureName: "Company Dropdown", description: "Company list", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.2.3", featureName: "Company Option", description: "Single company", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.1.3", featureName: "Notifications Bell", description: "Notification icon", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.3.1", featureName: "Unread Count Badge", description: "Unread count", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.3.2", featureName: "Notifications Dropdown", description: "Notification list", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.3.3", featureName: "Notification Item", description: "Single notification", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.3.4", featureName: "Mark All Read Button", description: "Clear all", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.1.4", featureName: "User Menu", description: "User dropdown", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.4.1", featureName: "User Avatar", description: "Profile picture", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.4.2", featureName: "Profile Link", description: "Go to profile", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.4.3", featureName: "Settings Link", description: "Go to settings", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.1.4.4", featureName: "Logout Button", description: "Sign out", location: "Global", screenshotRef: "" },
  
  { level: 1, hierarchy: "12.2", featureName: "Sidebar Navigation", description: "Left sidebar menu", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.1", featureName: "Dashboard Link", description: "Go to dashboard", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.2", featureName: "Campaigns Link", description: "Go to campaigns", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.3", featureName: "Inventories Link", description: "Go to inventories", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.4", featureName: "Creatives Link", description: "Go to creatives", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.5", featureName: "Proposals Link", description: "Go to proposals", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.6", featureName: "Statements Link", description: "Go to statements", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.7", featureName: "Tags Link", description: "Go to tags", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.8", featureName: "Reservations Link", description: "Go to reservations", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.9", featureName: "Reports Link", description: "Go to reports", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.10", featureName: "Settings Link", description: "Go to settings", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.2.11", featureName: "Collapse Sidebar Button", description: "Toggle sidebar", location: "Global", screenshotRef: "" },
  
  { level: 1, hierarchy: "12.3", featureName: "Inactivity Detection", description: "Session timeout", location: "Global", screenshotRef: "" },
  { level: 2, hierarchy: "12.3.1", featureName: "Warning Dialog", description: "30-min warning", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.3.1.1", featureName: "Warning Message", description: "Timeout warning text", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.3.1.2", featureName: "Countdown Timer", description: "1-min countdown", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.3.1.3", featureName: "Stay Logged In Button", description: "Extend session", location: "Global", screenshotRef: "" },
  { level: 3, hierarchy: "12.3.1.4", featureName: "Logout Now Button", description: "Immediate logout", location: "Global", screenshotRef: "" },

  // ==================== 13. CAMPAIGN APPROVAL MODULE ====================
  { level: 0, hierarchy: "13", featureName: "Campaign Approval", description: "Approval workflow module", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 1, hierarchy: "13.1", featureName: "Approval Sheet", description: "Approval side panel", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 2, hierarchy: "13.1.1", featureName: "Sheet Header", description: "Title area", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.1.1", featureName: "Sheet Title", description: "Approval heading", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.1.2", featureName: "Close Button", description: "Close sheet", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 2, hierarchy: "13.1.2", featureName: "Stage Timeline", description: "Approval stages", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.2.1", featureName: "Agency Acceptance Stage", description: "Stage 1", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.2.2", featureName: "Platform Review Stage", description: "Stage 2", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.2.3", featureName: "Media Owner Approval Stage", description: "Stage 3", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.2.4", featureName: "Stage Status Badge", description: "Pending/Approved", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.2.5", featureName: "Stage Timestamp", description: "When completed", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 2, hierarchy: "13.1.3", featureName: "Media Owner Tracker", description: "Per-MO approvals", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.3.1", featureName: "Media Owner Row", description: "Single MO", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.3.2", featureName: "MO Status Badge", description: "MO approval status", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.3.3", featureName: "Inventory Count", description: "Items from MO", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 2, hierarchy: "13.1.4", featureName: "Approval Actions", description: "Action buttons", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.4.1", featureName: "Approve Button", description: "Approve campaign", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.4.2", featureName: "Reject Button", description: "Reject campaign", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.4.3", featureName: "Request Changes Button", description: "Request edits", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.4.4", featureName: "Escalate Button", description: "Escalate issue", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 2, hierarchy: "13.1.5", featureName: "Decision Options", description: "Partial approval", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.5.1", featureName: "Proceed Anyway Option", description: "Continue without", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.5.2", featureName: "Wait for All Option", description: "Wait for MOs", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 2, hierarchy: "13.1.6", featureName: "Comments Section", description: "Approval notes", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.6.1", featureName: "Comment Input", description: "Add comment", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.6.2", featureName: "Submit Comment Button", description: "Post comment", location: "/campaigns/:id/approval", screenshotRef: "" },
  { level: 3, hierarchy: "13.1.6.3", featureName: "Comment History", description: "Previous comments", location: "/campaigns/:id/approval", screenshotRef: "" },

  // ==================== 14. MEDIA PLAN PRESENTATION ====================
  { level: 0, hierarchy: "14", featureName: "Media Plan Presentation", description: "Campaign presentation module", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 1, hierarchy: "14.1", featureName: "Media Plan Page", description: "Presentation view", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 2, hierarchy: "14.1.1", featureName: "Controls Bar", description: "Top controls", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.1.1", featureName: "View Type Selector", description: "Presentation/Analytics", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.1.2", featureName: "Theme Selector", description: "Visual theme", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.1.3", featureName: "Download Button", description: "Export options", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.1.4", featureName: "Share Button", description: "Share link", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.1.5", featureName: "Present Mode Button", description: "Full screen", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 2, hierarchy: "14.1.2", featureName: "Analytics Tabs", description: "Data tabs", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.2.1", featureName: "Campaign Plan Tab", description: "Overview tab", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.2.2", featureName: "Inventory Details Tab", description: "Inventory tab", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.2.3", featureName: "Costing Tab", description: "Costs tab", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.2.4", featureName: "Operations Tab", description: "Ops tab", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.2.5", featureName: "DOOH Schedules Tab", description: "Schedules tab", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 2, hierarchy: "14.1.3", featureName: "Campaign Summary", description: "Key metrics", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.1", featureName: "Campaign Name Display", description: "Name", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.2", featureName: "Date Range Display", description: "Dates", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.3", featureName: "Budget Display", description: "Budget", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.4", featureName: "Impressions KPI", description: "Impressions", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.5", featureName: "Reach KPI", description: "Reach", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.6", featureName: "Frequency KPI", description: "Frequency", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.3.7", featureName: "CPM KPI", description: "CPM", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 2, hierarchy: "14.1.4", featureName: "Share Modal", description: "Share dialog", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.4.1", featureName: "Share Link Display", description: "Generated URL", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.4.2", featureName: "Copy Link Button", description: "Copy to clipboard", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.4.3", featureName: "Include Theme Toggle", description: "Share with theme", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.4.4", featureName: "Close Modal Button", description: "Close dialog", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 2, hierarchy: "14.1.5", featureName: "Map Preview", description: "Geographic view", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.5.1", featureName: "Map Canvas", description: "Map display", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.5.2", featureName: "Inventory Markers", description: "Location pins", location: "/campaigns/:id/media-plan", screenshotRef: "" },
  { level: 3, hierarchy: "14.1.5.3", featureName: "Expand Map Button", description: "Open full map", location: "/campaigns/:id/media-plan", screenshotRef: "" },

  // ==================== 15. AVAILABILITY VIEW ====================
  { level: 0, hierarchy: "15", featureName: "Availability View", description: "Calendar availability module", location: "/availability-view", screenshotRef: "" },
  { level: 1, hierarchy: "15.1", featureName: "Availability Page", description: "Calendar interface", location: "/availability-view", screenshotRef: "" },
  { level: 2, hierarchy: "15.1.1", featureName: "Page Header", description: "Title area", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.1.1", featureName: "Page Title", description: "Availability heading", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.1.2", featureName: "Date Range Selector", description: "View period", location: "/availability-view", screenshotRef: "" },
  { level: 2, hierarchy: "15.1.2", featureName: "View Toggle", description: "Calendar/Timeline", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.2.1", featureName: "Calendar View Button", description: "Monthly view", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.2.2", featureName: "Timeline View Button", description: "Gantt view", location: "/availability-view", screenshotRef: "" },
  { level: 2, hierarchy: "15.1.3", featureName: "Filter Panel", description: "Inventory filters", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.3.1", featureName: "Inventory Search", description: "Search filter", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.3.2", featureName: "Type Filter", description: "Inventory type", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.3.3", featureName: "Location Filter", description: "Location filter", location: "/availability-view", screenshotRef: "" },
  { level: 2, hierarchy: "15.1.4", featureName: "Calendar Grid", description: "Availability display", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.4.1", featureName: "Inventory Row", description: "Single inventory", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.4.2", featureName: "Day Cell", description: "Single day", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.4.3", featureName: "Available Indicator", description: "Green available", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.4.4", featureName: "Booked Indicator", description: "Red booked", location: "/availability-view", screenshotRef: "" },
  { level: 3, hierarchy: "15.1.4.5", featureName: "Hold Indicator", description: "Yellow on hold", location: "/availability-view", screenshotRef: "" },

  // ==================== 16. MAP VIEW ====================
  { level: 0, hierarchy: "16", featureName: "Map View", description: "Geographic map module", location: "/map-view", screenshotRef: "" },
  { level: 1, hierarchy: "16.1", featureName: "Map Page", description: "Full map interface", location: "/map-view", screenshotRef: "" },
  { level: 2, hierarchy: "16.1.1", featureName: "Map Canvas", description: "Interactive map", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.1.1", featureName: "Inventory Markers", description: "Location pins", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.1.2", featureName: "Marker Popup", description: "Pin info popup", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.1.3", featureName: "Zoom Controls", description: "Zoom in/out", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.1.4", featureName: "Reset View Button", description: "Reset position", location: "/map-view", screenshotRef: "" },
  { level: 2, hierarchy: "16.1.2", featureName: "Drawing Tools", description: "Selection tools", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.2.1", featureName: "Polygon Tool", description: "Draw polygon", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.2.2", featureName: "Radius Tool", description: "Draw radius", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.2.3", featureName: "Clear Selection Button", description: "Clear drawing", location: "/map-view", screenshotRef: "" },
  { level: 2, hierarchy: "16.1.3", featureName: "Layer Controls", description: "Map layers", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.3.1", featureName: "Heatmap Toggle", description: "Show heatmap", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.3.2", featureName: "Cluster Toggle", description: "Cluster markers", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.3.3", featureName: "POI Toggle", description: "Show POIs", location: "/map-view", screenshotRef: "" },
  { level: 2, hierarchy: "16.1.4", featureName: "Selection Panel", description: "Selected inventories", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.4.1", featureName: "Selected Count", description: "Items selected", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.4.2", featureName: "Selected List", description: "List of selected", location: "/map-view", screenshotRef: "" },
  { level: 3, hierarchy: "16.1.4.3", featureName: "Add to Campaign Button", description: "Add selection", location: "/map-view", screenshotRef: "" },
];

// Transform to full feature entries with computed functions
const features: FeatureEntry[] = featureData.map(f => ({
  ...f,
  defaultFunctions: getDefaultFunctions(f.hierarchy, f.featureName)
}));

// Generate Excel workbook
const wb = XLSX.utils.book_new();

// Convert features array to worksheet data with Functions column
const wsData = [
  ["Level", "Hierarchy ID", "Feature Name", "Description", "Location/Route", "Default Functions", "Screenshot Reference"],
  ...features.map(f => [f.level, f.hierarchy, f.featureName, f.description, f.location, f.defaultFunctions, f.screenshotRef])
];

const ws = XLSX.utils.aoa_to_sheet(wsData);

// Set column widths
ws["!cols"] = [
  { wch: 8 },   // Level
  { wch: 15 },  // Hierarchy ID
  { wch: 40 },  // Feature Name
  { wch: 50 },  // Description
  { wch: 35 },  // Location
  { wch: 60 },  // Default Functions
  { wch: 50 },  // Screenshot Reference
];

// Add worksheet to workbook
XLSX.utils.book_append_sheet(wb, ws, "Feature Inventory");

// Generate Functions Reference sheet
const functionsData = [
  ["Company Type", "Function Name", "Description"],
  ["Media Owner", "Operations Manager", "Full inventory management, availability, rate cards"],
  ["Media Owner", "Creative Approver", "Approve/reject creative submissions"],
  ["Media Owner", "Sales Rep", "Sales activities, client relationships"],
  ["Media Owner", "Billing Admin", "Invoice management, payment tracking"],
  ["Media Owner", "Viewer", "Read-only access to permitted areas"],
  ["Media Agency", "Campaign Planner", "Create and plan campaigns"],
  ["Media Agency", "Media Buyer", "Negotiate prices, book inventory, manage budgets"],
  ["Media Agency", "Account Director", "Full access, approvals, client oversight"],
  ["Media Agency", "Creative Manager", "Upload and manage creatives"],
  ["Media Agency", "Viewer", "Read-only access to permitted areas"],
  ["Advertiser", "Brand Manager", "Manage brand campaigns and approvals"],
  ["Advertiser", "Approver", "Approve campaigns and creatives"],
  ["Advertiser", "Viewer", "Read-only access to permitted areas"],
  ["Reseller", "Sales Manager", "Manage reseller sales activities"],
  ["Reseller", "Operations", "Inventory allocation and management"],
  ["Reseller", "Viewer", "Read-only access to permitted areas"],
  ["Internal", "Super Admin", "Full system access"],
  ["Internal", "Account Manager", "Manage client companies and contracts"],
  ["Internal", "Ad Ops", "Campaign operations and troubleshooting"],
  ["Internal", "Support", "Customer support with tenant switching"],
  ["Internal", "Finance", "Financial reports and billing"],
  ["Internal", "Security", "Security audits and access reviews"],
];

const wsFunctions = XLSX.utils.aoa_to_sheet(functionsData);
wsFunctions["!cols"] = [
  { wch: 15 },  // Company Type
  { wch: 25 },  // Function Name
  { wch: 50 },  // Description
];
XLSX.utils.book_append_sheet(wb, wsFunctions, "Functions Reference");

// Write to file
XLSX.writeFile(wb, "docs/MW-Planner-Feature-Inventory.xlsx");

console.log(`Excel file generated: docs/MW-Planner-Feature-Inventory.xlsx`);
console.log(`Total features documented: ${features.length}`);
