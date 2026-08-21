import * as XLSX from 'xlsx';
import * as fs from 'fs';
import * as path from 'path';

// Complete OOH Industry Standard Inventory Taxonomy
const inventoryTaxonomy = [
  // DIGITAL OUTDOOR - BILLBOARD
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Digital Billboard', sizeCategory: 'XL', dimensions: '14m x 48m (672 sq m)', aspectRatio: '7:24', typicalLocations: 'Highways, Major Roads', notes: 'Large format digital display' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Digital Spectacular', sizeCategory: 'XL', dimensions: '20m x 60m (1200 sq m)', aspectRatio: 'Custom', typicalLocations: 'Premium Locations, Times Square', notes: 'Premium digital displays with unique shapes' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'LED Billboard', sizeCategory: 'L', dimensions: '6m x 12m (72 sq m)', aspectRatio: '1:2', typicalLocations: 'Main Roads, Intersections', notes: 'Standard LED display' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Digital Poster', sizeCategory: 'M', dimensions: '3m x 4m (12 sq m)', aspectRatio: '3:4', typicalLocations: 'Urban Areas, Shopping Districts', notes: 'Medium format digital poster' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Billboard', format: 'Digital Billboard Network', sizeCategory: 'L', dimensions: '6m x 12m (72 sq m)', aspectRatio: '1:2', typicalLocations: 'Multiple Locations', notes: 'Network of synchronized digital billboards' },

  // CLASSIC OUTDOOR - BILLBOARD
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Bulletin', sizeCategory: 'XL', dimensions: '14m x 48m (672 sq m)', aspectRatio: '7:24', typicalLocations: 'Highways, Major Roads', notes: 'Traditional large format billboard' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Poster Panel', sizeCategory: 'L', dimensions: '3.5m x 7m (24.5 sq m)', aspectRatio: '1:2', typicalLocations: 'Urban Areas', notes: 'Standard poster billboard' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: '48 Sheet', sizeCategory: 'L', dimensions: '6m x 3m (18 sq m)', aspectRatio: '2:1', typicalLocations: 'Roadsides, City Centers', notes: 'UK/Europe standard large poster' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: '96 Sheet', sizeCategory: 'XL', dimensions: '12m x 3m (36 sq m)', aspectRatio: '4:1', typicalLocations: 'Major Roads', notes: 'Large format outdoor poster' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Wallscape', sizeCategory: 'XL', dimensions: '15m x 25m (375 sq m)', aspectRatio: 'Variable', typicalLocations: 'Building Walls', notes: 'Building-mounted large displays' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Billboard', format: 'Rooftop Billboard', sizeCategory: 'XL', dimensions: '10m x 30m (300 sq m)', aspectRatio: '1:3', typicalLocations: 'Building Rooftops', notes: 'Elevated billboard on buildings' },

  // DIGITAL OUTDOOR - STREET FURNITURE
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: 'Digital Bus Shelter', sizeCategory: 'M', dimensions: '1.2m x 1.8m (2.16 sq m)', aspectRatio: '2:3', typicalLocations: 'Bus Stops', notes: 'Interactive digital displays at bus shelters' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: 'Digital Kiosk', sizeCategory: 'M', dimensions: '2m x 1.5m (3 sq m)', aspectRatio: '4:3', typicalLocations: 'City Centers, Plazas', notes: 'Freestanding digital information kiosks' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: 'Smart City Panel', sizeCategory: 'M', dimensions: '1.8m x 1.2m (2.16 sq m)', aspectRatio: '3:2', typicalLocations: 'Urban Streets', notes: 'Multi-functional smart city displays' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: 'Digital Phone Booth', sizeCategory: 'S', dimensions: '0.8m x 1.5m (1.2 sq m)', aspectRatio: '8:15', typicalLocations: 'Sidewalks', notes: 'Modern digital phone booth displays' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Street Furniture', format: 'Digital Column', sizeCategory: 'M', dimensions: '3m x 1m (3 sq m)', aspectRatio: '3:1', typicalLocations: 'Pedestrian Areas', notes: 'Vertical cylindrical digital displays' },

  // CLASSIC OUTDOOR - STREET FURNITURE
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: 'Bus Shelter', sizeCategory: 'M', dimensions: '1.2m x 1.8m (2.16 sq m)', aspectRatio: '2:3', typicalLocations: 'Bus Stops', notes: 'Standard illuminated bus shelter poster' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Street Furniture', format: 'Phone Kiosk', sizeCategory: 'S', dimensions: '0.8m x 1.2m (0.96 sq m)', aspectRatio: '2:3', typicalLocations: 'Sidewalks', notes: 'Traditional phone booth advertising' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: '6 Sheet Poster', sizeCategory: 'S', dimensions: '1.2m x 1.8m (2.16 sq m)', aspectRatio: '2:3', typicalLocations: 'Urban Areas', notes: 'Small format poster displays' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Street Furniture', format: 'Bench Advertising', sizeCategory: 'S', dimensions: '0.4m x 1.5m (0.6 sq m)', aspectRatio: '15:4', typicalLocations: 'Parks, Bus Stops', notes: 'Advertising on bench backs' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Street Furniture', format: 'Litter Bin', sizeCategory: 'S', dimensions: '0.3m x 0.8m (0.24 sq m)', aspectRatio: '3:8', typicalLocations: 'Streets, Parks', notes: 'Advertising on waste bins' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Street Furniture', format: 'Advertising Column', sizeCategory: 'M', dimensions: '3m x 1m (3 sq m)', aspectRatio: '3:1', typicalLocations: 'City Centers', notes: 'Morris Column / Litfass Column' },

  // DIGITAL TRANSIT - BUS
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Outside Digital', sizeCategory: 'L', dimensions: '12m x 2.5m (30 sq m)', aspectRatio: '24:5', typicalLocations: 'Bus Exterior', notes: 'Full bus wrap with digital screens' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Bus Inside Screen', sizeCategory: 'S', dimensions: '0.4m x 0.7m (0.28 sq m)', aspectRatio: '4:7', typicalLocations: 'Bus Interior', notes: 'Digital screens inside buses' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Side Digital Panel', sizeCategory: 'M', dimensions: '5m x 2m (10 sq m)', aspectRatio: '5:2', typicalLocations: 'Bus Side', notes: 'Digital display on bus sides' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Rear Digital', sizeCategory: 'M', dimensions: '2.5m x 2m (5 sq m)', aspectRatio: '5:4', typicalLocations: 'Bus Rear', notes: 'Digital screen on bus back' },

  // CLASSIC TRANSIT - BUS
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Full Wrap', sizeCategory: 'L', dimensions: '12m x 2.5m (30 sq m)', aspectRatio: '24:5', typicalLocations: 'Bus Exterior', notes: 'Complete bus exterior coverage' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus King (T-Side)', sizeCategory: 'M', dimensions: '9m x 1.2m (10.8 sq m)', aspectRatio: '15:2', typicalLocations: 'Bus Side', notes: 'Side panel advertising' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Queen (Half Side)', sizeCategory: 'M', dimensions: '4.5m x 1.2m (5.4 sq m)', aspectRatio: '15:4', typicalLocations: 'Bus Side', notes: 'Half-side panel advertising' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Rear Panel', sizeCategory: 'M', dimensions: '2.5m x 2m (5 sq m)', aspectRatio: '5:4', typicalLocations: 'Bus Rear', notes: 'Back panel advertising' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bus Superside', sizeCategory: 'L', dimensions: '12m x 1.5m (18 sq m)', aspectRatio: '8:1', typicalLocations: 'Bus Top Side', notes: 'Upper side panel advertising' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Bus Interior Card', sizeCategory: 'S', dimensions: '0.3m x 0.6m (0.18 sq m)', aspectRatio: '1:2', typicalLocations: 'Bus Interior', notes: 'Small cards inside buses' },

  // DIGITAL TRANSIT - TAXI
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Taxi Top Digital', sizeCategory: 'S', dimensions: '0.8m x 0.4m (0.32 sq m)', aspectRatio: '2:1', typicalLocations: 'Taxi Roof', notes: 'Digital LED taxi top display' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Taxi Inside Screen', sizeCategory: 'S', dimensions: '0.25m x 0.15m (0.0375 sq m)', aspectRatio: '5:3', typicalLocations: 'Taxi Interior', notes: 'Rear passenger screen' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Taxi Door Digital', sizeCategory: 'S', dimensions: '0.5m x 0.8m (0.4 sq m)', aspectRatio: '5:8', typicalLocations: 'Taxi Doors', notes: 'Digital displays on taxi doors' },

  // CLASSIC TRANSIT - TAXI
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Taxi Top', sizeCategory: 'S', dimensions: '0.8m x 0.4m (0.32 sq m)', aspectRatio: '2:1', typicalLocations: 'Taxi Roof', notes: 'Illuminated taxi roof sign' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Taxi Full Wrap', sizeCategory: 'M', dimensions: '4m x 1.5m (6 sq m)', aspectRatio: '8:3', typicalLocations: 'Taxi Exterior', notes: 'Full vehicle wrap' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Taxi Door Panel', sizeCategory: 'S', dimensions: '0.5m x 0.8m (0.4 sq m)', aspectRatio: '5:8', typicalLocations: 'Taxi Doors', notes: 'Door panel advertising' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Taxi Receipt', sizeCategory: 'S', dimensions: '0.08m x 0.15m (0.012 sq m)', aspectRatio: '8:15', typicalLocations: 'Taxi Receipt', notes: 'Advertising on receipts' },

  // DIGITAL TRANSIT - TRAIN/METRO
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Train Inside Screen', sizeCategory: 'M', dimensions: '1m x 0.6m (0.6 sq m)', aspectRatio: '5:3', typicalLocations: 'Train/Metro Interior', notes: 'Digital screens in trains' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Train Exterior Digital', sizeCategory: 'L', dimensions: '20m x 3m (60 sq m)', aspectRatio: '20:3', typicalLocations: 'Train Exterior', notes: 'Digital displays on train sides' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Platform Digital Screen', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Station Platforms', notes: 'Large platform digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Turnstile Screen', sizeCategory: 'S', dimensions: '0.5m x 0.8m (0.4 sq m)', aspectRatio: '5:8', typicalLocations: 'Station Entrance', notes: 'Digital screens at entry gates' },

  // CLASSIC TRANSIT - TRAIN/METRO
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Train Full Wrap', sizeCategory: 'XL', dimensions: '20m x 3m (60 sq m)', aspectRatio: '20:3', typicalLocations: 'Train Exterior', notes: 'Complete train wrap' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Train Car Card', sizeCategory: 'S', dimensions: '0.4m x 0.6m (0.24 sq m)', aspectRatio: '2:3', typicalLocations: 'Train Interior', notes: 'Interior advertising cards' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Platform Poster', sizeCategory: 'L', dimensions: '2m x 3m (6 sq m)', aspectRatio: '2:3', typicalLocations: 'Station Platforms', notes: 'Large platform posters' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Escalator Panel', sizeCategory: 'M', dimensions: '1m x 1.5m (1.5 sq m)', aspectRatio: '2:3', typicalLocations: 'Escalator Sides', notes: 'Panels along escalators' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Station Domination', sizeCategory: 'XL', dimensions: 'Multiple', aspectRatio: 'Various', typicalLocations: 'Entire Station', notes: 'Full station takeover' },

  // DIGITAL TRANSIT - AIRPORT
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Airport Digital Screen', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Airport Terminals', notes: 'Large terminal digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Baggage Claim Screen', sizeCategory: 'M', dimensions: '2m x 1.5m (3 sq m)', aspectRatio: '4:3', typicalLocations: 'Baggage Area', notes: 'Screens at baggage claim' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Gate Digital Display', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Boarding Gates', notes: 'Digital displays at gates' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Jetway Digital', sizeCategory: 'M', dimensions: '1m x 0.6m (0.6 sq m)', aspectRatio: '5:3', typicalLocations: 'Jet Bridges', notes: 'Screens in boarding bridges' },

  // CLASSIC TRANSIT - AIRPORT
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Airport Light Box', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Airport Terminals', notes: 'Backlit airport displays' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Baggage Carousel', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Baggage Claim', notes: 'Advertising on carousels' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Individual', type: 'Transit', format: 'Terminal Domination', sizeCategory: 'XL', dimensions: 'Multiple', aspectRatio: 'Various', typicalLocations: 'Entire Terminal', notes: 'Full terminal takeover' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Transit', format: 'Airport Diorama', sizeCategory: 'L', dimensions: '4m x 2m (8 sq m)', aspectRatio: '2:1', typicalLocations: 'Terminal Halls', notes: 'Large backlit displays' },

  // DIGITAL INDOOR - RETAIL
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Mall Digital Screen', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Shopping Malls', notes: 'Large mall digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Retail Window Screen', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Store Windows', notes: 'Digital window displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Point of Sale Screen', sizeCategory: 'S', dimensions: '0.5m x 0.3m (0.15 sq m)', aspectRatio: '5:3', typicalLocations: 'Checkout Counters', notes: 'Small POS displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Supermarket Screen', sizeCategory: 'M', dimensions: '1m x 0.8m (0.8 sq m)', aspectRatio: '5:4', typicalLocations: 'Supermarket Aisles', notes: 'Aisle digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Food Court Digital', sizeCategory: 'L', dimensions: '2m x 1.5m (3 sq m)', aspectRatio: '4:3', typicalLocations: 'Food Courts', notes: 'Food court menu boards' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Elevator Screen', sizeCategory: 'S', dimensions: '0.4m x 0.6m (0.24 sq m)', aspectRatio: '2:3', typicalLocations: 'Elevators', notes: 'Screens inside elevators' },

  // CLASSIC INDOOR - RETAIL
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Mall Poster', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Shopping Malls', notes: 'Large mall posters' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Retail Light Box', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Store Fronts', notes: 'Backlit retail displays' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Shelf Talker', sizeCategory: 'S', dimensions: '0.15m x 0.1m (0.015 sq m)', aspectRatio: '3:2', typicalLocations: 'Store Shelves', notes: 'Small shelf displays' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Shopping Cart Ad', sizeCategory: 'S', dimensions: '0.3m x 0.2m (0.06 sq m)', aspectRatio: '3:2', typicalLocations: 'Shopping Carts', notes: 'Cart advertising panels' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Retail', format: 'Floor Graphics', sizeCategory: 'M', dimensions: '1m x 1m (1 sq m)', aspectRatio: '1:1', typicalLocations: 'Store Floors', notes: 'Floor adhesive graphics' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Individual', type: 'Retail', format: 'Retail Domination', sizeCategory: 'XL', dimensions: 'Multiple', aspectRatio: 'Various', typicalLocations: 'Entire Store', notes: 'Full store takeover' },

  // DIGITAL INDOOR - VENUES
  { classification: 'Digital', category: 'Indoor', networkType: 'Individual', type: 'Venue', format: 'Cinema Screen Ad', sizeCategory: 'XL', dimensions: '10m x 5m (50 sq m)', aspectRatio: '2:1', typicalLocations: 'Movie Theaters', notes: 'Pre-movie digital advertising' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Cinema Lobby Screen', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Cinema Lobbies', notes: 'Lobby digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Stadium LED Board', sizeCategory: 'XL', dimensions: '20m x 10m (200 sq m)', aspectRatio: '2:1', typicalLocations: 'Sports Stadiums', notes: 'Large stadium LED displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Gym Screen', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Fitness Centers', notes: 'Gym digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Bar/Restaurant Screen', sizeCategory: 'M', dimensions: '1m x 0.6m (0.6 sq m)', aspectRatio: '5:3', typicalLocations: 'Bars & Restaurants', notes: 'Hospitality venue screens' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Hotel Lobby Screen', sizeCategory: 'L', dimensions: '2m x 1.5m (3 sq m)', aspectRatio: '4:3', typicalLocations: 'Hotel Lobbies', notes: 'Hotel digital displays' },

  // CLASSIC INDOOR - VENUES
  { classification: 'Classic', category: 'Indoor', networkType: 'Individual', type: 'Venue', format: 'Cinema Poster', sizeCategory: 'L', dimensions: '2m x 3m (6 sq m)', aspectRatio: '2:3', typicalLocations: 'Movie Theaters', notes: 'Movie poster displays' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Stadium Banner', sizeCategory: 'XL', dimensions: '10m x 2m (20 sq m)', aspectRatio: '5:1', typicalLocations: 'Sports Venues', notes: 'Large venue banners' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Restroom Poster', sizeCategory: 'S', dimensions: '0.5m x 0.7m (0.35 sq m)', aspectRatio: '5:7', typicalLocations: 'Venue Restrooms', notes: 'Restroom advertising panels' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Venue', format: 'Table Tent', sizeCategory: 'S', dimensions: '0.15m x 0.1m (0.015 sq m)', aspectRatio: '3:2', typicalLocations: 'Restaurant Tables', notes: 'Table-top advertising' },

  // DIGITAL OUTDOOR - SPECIAL FORMATS
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Building Projection', sizeCategory: 'XL', dimensions: '30m x 40m (1200 sq m)', aspectRatio: 'Variable', typicalLocations: 'Building Facades', notes: 'Large building projections' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Special Format', format: 'Interactive Kiosk', sizeCategory: 'M', dimensions: '2m x 1.5m (3 sq m)', aspectRatio: '4:3', typicalLocations: 'Public Spaces', notes: 'Touch-enabled digital kiosks' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Bridge Banner Digital', sizeCategory: 'XL', dimensions: '10m x 3m (30 sq m)', aspectRatio: '10:3', typicalLocations: 'Pedestrian Bridges', notes: 'Digital displays on bridges' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Special Format', format: 'Gas Pump Screen', sizeCategory: 'S', dimensions: '0.4m x 0.3m (0.12 sq m)', aspectRatio: '4:3', typicalLocations: 'Gas Stations', notes: 'Fuel pump digital screens' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Special Format', format: 'ATM Screen', sizeCategory: 'S', dimensions: '0.3m x 0.2m (0.06 sq m)', aspectRatio: '3:2', typicalLocations: 'ATM Machines', notes: 'ATM advertising screens' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Digital Pylon', sizeCategory: 'L', dimensions: '5m x 8m (40 sq m)', aspectRatio: '5:8', typicalLocations: 'Highways, Malls', notes: 'Tall digital sign posts' },

  // CLASSIC OUTDOOR - SPECIAL FORMATS
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Bridge Banner', sizeCategory: 'XL', dimensions: '10m x 3m (30 sq m)', aspectRatio: '10:3', typicalLocations: 'Pedestrian Bridges', notes: 'Large bridge banners' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Pole Banner', sizeCategory: 'M', dimensions: '2m x 0.8m (1.6 sq m)', aspectRatio: '5:2', typicalLocations: 'Street Poles', notes: 'Street pole banners' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Mobile Billboard', sizeCategory: 'L', dimensions: '5m x 3m (15 sq m)', aspectRatio: '5:3', typicalLocations: 'Mobile Trucks', notes: 'Truck-mounted billboards' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Aerial Banner', sizeCategory: 'L', dimensions: '15m x 1.5m (22.5 sq m)', aspectRatio: '10:1', typicalLocations: 'Airborne', notes: 'Airplane-towed banners' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Wild Posting', sizeCategory: 'M', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'Construction Sites', notes: 'Poster on construction barriers' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Individual', type: 'Special Format', format: 'Pylon Sign', sizeCategory: 'L', dimensions: '5m x 8m (40 sq m)', aspectRatio: '5:8', typicalLocations: 'Highways, Malls', notes: 'Tall static sign posts' },

  // DIGITAL TRANSIT - OTHER
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Truck Side Digital', sizeCategory: 'L', dimensions: '7m x 2.5m (17.5 sq m)', aspectRatio: '14:5', typicalLocations: 'Delivery Trucks', notes: 'Digital truck-side advertising' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bike Share Digital', sizeCategory: 'S', dimensions: '0.4m x 0.3m (0.12 sq m)', aspectRatio: '4:3', typicalLocations: 'Bike Stations', notes: 'Digital displays on bike shares' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Scooter Digital', sizeCategory: 'S', dimensions: '0.2m x 0.15m (0.03 sq m)', aspectRatio: '4:3', typicalLocations: 'E-Scooters', notes: 'Small scooter displays' },

  // CLASSIC TRANSIT - OTHER
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Truck Full Wrap', sizeCategory: 'L', dimensions: '7m x 2.5m (17.5 sq m)', aspectRatio: '14:5', typicalLocations: 'Delivery Trucks', notes: 'Full truck wraps' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Bike Share Panel', sizeCategory: 'S', dimensions: '0.4m x 0.3m (0.12 sq m)', aspectRatio: '4:3', typicalLocations: 'Bike Stations', notes: 'Bike station advertising' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Pedicab Advertising', sizeCategory: 'S', dimensions: '0.8m x 0.6m (0.48 sq m)', aspectRatio: '4:3', typicalLocations: 'Pedicabs', notes: 'Cycle rickshaw advertising' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Tram/Streetcar Wrap', sizeCategory: 'L', dimensions: '15m x 2.5m (37.5 sq m)', aspectRatio: '6:1', typicalLocations: 'Trams', notes: 'Full tram wraps' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Transit', format: 'Ferry Advertising', sizeCategory: 'L', dimensions: '10m x 3m (30 sq m)', aspectRatio: '10:3', typicalLocations: 'Ferries', notes: 'Ferry boat advertising' },

  // DIGITAL INDOOR - HEALTHCARE & EDUCATION
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Healthcare', format: 'Clinic Waiting Room Screen', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Medical Clinics', notes: 'Waiting area digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Healthcare', format: 'Hospital Digital Display', sizeCategory: 'L', dimensions: '2m x 1.5m (3 sq m)', aspectRatio: '4:3', typicalLocations: 'Hospitals', notes: 'Hospital corridor displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Healthcare', format: 'Pharmacy Screen', sizeCategory: 'M', dimensions: '1m x 0.8m (0.8 sq m)', aspectRatio: '5:4', typicalLocations: 'Pharmacies', notes: 'Pharmacy digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Education', format: 'University Digital Board', sizeCategory: 'L', dimensions: '3m x 2m (6 sq m)', aspectRatio: '3:2', typicalLocations: 'University Campus', notes: 'Campus digital displays' },
  { classification: 'Digital', category: 'Indoor', networkType: 'Network', type: 'Education', format: 'School Cafeteria Screen', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'School Cafeterias', notes: 'Cafeteria digital screens' },

  // CLASSIC INDOOR - HEALTHCARE & EDUCATION
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Healthcare', format: 'Clinic Poster', sizeCategory: 'M', dimensions: '1m x 1.5m (1.5 sq m)', aspectRatio: '2:3', typicalLocations: 'Medical Clinics', notes: 'Waiting room posters' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Healthcare', format: 'Pharmacy Light Box', sizeCategory: 'M', dimensions: '1m x 0.8m (0.8 sq m)', aspectRatio: '5:4', typicalLocations: 'Pharmacies', notes: 'Backlit pharmacy displays' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Education', format: 'Campus Poster', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'Universities', notes: 'Campus bulletin boards' },
  { classification: 'Classic', category: 'Indoor', networkType: 'Network', type: 'Education', format: 'Student Union Banner', sizeCategory: 'L', dimensions: '3m x 1.5m (4.5 sq m)', aspectRatio: '2:1', typicalLocations: 'Student Centers', notes: 'Large campus banners' },

  // DIGITAL OUTDOOR - URBAN FURNITURE
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'Digital Newsstand', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'News Kiosks', notes: 'Digital newsstand displays' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'Digital Parking Meter', sizeCategory: 'S', dimensions: '0.3m x 0.5m (0.15 sq m)', aspectRatio: '3:5', typicalLocations: 'Parking Meters', notes: 'Parking meter screens' },
  { classification: 'Digital', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'Digital Bollard', sizeCategory: 'S', dimensions: '0.4m x 0.8m (0.32 sq m)', aspectRatio: '1:2', typicalLocations: 'Sidewalks', notes: 'Digital safety bollards' },

  // CLASSIC OUTDOOR - URBAN FURNITURE
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'Newsstand Panel', sizeCategory: 'S', dimensions: '0.8m x 1.2m (0.96 sq m)', aspectRatio: '2:3', typicalLocations: 'News Kiosks', notes: 'Newsstand advertising panels' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'Parking Sign', sizeCategory: 'S', dimensions: '0.5m x 0.3m (0.15 sq m)', aspectRatio: '5:3', typicalLocations: 'Parking Areas', notes: 'Parking area signage' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'Waste Container', sizeCategory: 'S', dimensions: '0.3m x 0.5m (0.15 sq m)', aspectRatio: '3:5', typicalLocations: 'Public Areas', notes: 'Advertising on bins' },
  { classification: 'Classic', category: 'Outdoor', networkType: 'Network', type: 'Urban Furniture', format: 'City Information Panel', sizeCategory: 'M', dimensions: '1.5m x 1m (1.5 sq m)', aspectRatio: '3:2', typicalLocations: 'City Centers', notes: 'City map/info displays' },
];

// Create worksheet data with headers
const worksheetData = [
  [
    'Classification',
    'Category', 
    'Network Type',
    'Type',
    'Format',
    'Size Category',
    'Dimensions',
    'Aspect Ratio',
    'Typical Locations',
    'Notes'
  ],
  ...inventoryTaxonomy.map(item => [
    item.classification,
    item.category,
    item.networkType,
    item.type,
    item.format,
    item.sizeCategory,
    item.dimensions,
    item.aspectRatio,
    item.typicalLocations,
    item.notes
  ])
];

// Create workbook and worksheet
const wb = XLSX.utils.book_new();
const ws = XLSX.utils.aoa_to_sheet(worksheetData);

// Set column widths
ws['!cols'] = [
  { wch: 15 },  // Classification
  { wch: 12 },  // Category
  { wch: 15 },  // Network Type
  { wch: 18 },  // Type
  { wch: 30 },  // Format
  { wch: 15 },  // Size Category
  { wch: 25 },  // Dimensions
  { wch: 15 },  // Aspect Ratio
  { wch: 35 },  // Typical Locations
  { wch: 45 }   // Notes
];

// Add worksheet to workbook
XLSX.utils.book_append_sheet(wb, ws, 'OOH Inventory Taxonomy');

// Create summary statistics worksheet
const stats = inventoryTaxonomy.reduce((acc, item) => {
  // Count by classification
  acc.byClassification[item.classification] = (acc.byClassification[item.classification] || 0) + 1;
  
  // Count by category
  acc.byCategory[item.category] = (acc.byCategory[item.category] || 0) + 1;
  
  // Count by type
  acc.byType[item.type] = (acc.byType[item.type] || 0) + 1;
  
  // Count by size
  acc.bySize[item.sizeCategory] = (acc.bySize[item.sizeCategory] || 0) + 1;
  
  // Count by network type
  acc.byNetwork[item.networkType] = (acc.byNetwork[item.networkType] || 0) + 1;
  
  return acc;
}, {
  byClassification: {} as Record<string, number>,
  byCategory: {} as Record<string, number>,
  byType: {} as Record<string, number>,
  bySize: {} as Record<string, number>,
  byNetwork: {} as Record<string, number>
});

const summaryData = [
  ['OOH INVENTORY TAXONOMY SUMMARY'],
  [''],
  ['Total Formats:', inventoryTaxonomy.length],
  [''],
  ['BY CLASSIFICATION'],
  ...Object.entries(stats.byClassification).map(([key, value]) => [key, value]),
  [''],
  ['BY CATEGORY'],
  ...Object.entries(stats.byCategory).map(([key, value]) => [key, value]),
  [''],
  ['BY TYPE'],
  ...Object.entries(stats.byType).map(([key, value]) => [key, value]),
  [''],
  ['BY SIZE CATEGORY'],
  ...Object.entries(stats.bySize).map(([key, value]) => [key, value]),
  [''],
  ['BY NETWORK TYPE'],
  ...Object.entries(stats.byNetwork).map(([key, value]) => [key, value]),
];

const wsSummary = XLSX.utils.aoa_to_sheet(summaryData);
wsSummary['!cols'] = [{ wch: 30 }, { wch: 15 }];
XLSX.utils.book_append_sheet(wb, wsSummary, 'Summary');

// Create size reference worksheet
const sizeReference = [
  ['SIZE CATEGORY REFERENCE'],
  [''],
  ['Size Category', 'Minimum Area (sq m)', 'Maximum Area (sq m)', 'Examples'],
  ['S (Small)', '0.01', '2.5', 'Bus interior cards, shelf talkers, taxi receipts, parking signs'],
  ['M (Medium)', '2.5', '15', 'Bus shelters, retail light boxes, digital kiosks, platform posters'],
  ['L (Large)', '15', '100', 'Billboards, train wraps, mall screens, airport displays'],
  ['XL (Extra Large)', '100', '1500+', 'Spectaculars, building projections, stadium LEDs, station domination'],
  [''],
  ['ASPECT RATIO GUIDE'],
  [''],
  ['Aspect Ratio', 'Orientation', 'Common Formats'],
  ['16:9, 3:2, 5:3', 'Landscape', 'Digital screens, billboards, displays'],
  ['2:3, 9:16, 5:8', 'Portrait', 'Bus shelters, mobile screens, vertical displays'],
  ['1:1, 4:3, 5:4', 'Square/Near-Square', 'Social media optimized, retail displays'],
  ['Custom/Variable', 'Special', 'Spectaculars, building wraps, projections'],
];

const wsSize = XLSX.utils.aoa_to_sheet(sizeReference);
wsSize['!cols'] = [{ wch: 20 }, { wch: 25 }, { wch: 25 }, { wch: 50 }];
XLSX.utils.book_append_sheet(wb, wsSize, 'Size Reference');

// Ensure output directory exists
const outputDir = path.join(process.cwd(), 'attached_assets');
if (!fs.existsSync(outputDir)) {
  fs.mkdirSync(outputDir, { recursive: true });
}

// Write file
const outputPath = path.join(outputDir, 'OOH_Inventory_Taxonomy.xlsx');
XLSX.writeFile(wb, outputPath);

console.log(`✅ OOH Inventory Taxonomy Excel file created successfully!`);
console.log(`📁 Location: ${outputPath}`);
console.log(`📊 Total Formats: ${inventoryTaxonomy.length}`);
console.log(`\nBreakdown:`);
console.log(`- Digital formats: ${stats.byClassification['Digital'] || 0}`);
console.log(`- Classic formats: ${stats.byClassification['Classic'] || 0}`);
console.log(`- Indoor: ${stats.byCategory['Indoor'] || 0}`);
console.log(`- Outdoor: ${stats.byCategory['Outdoor'] || 0}`);
console.log(`- Network-based: ${stats.byNetwork['Network'] || 0}`);
console.log(`- Individual: ${stats.byNetwork['Individual'] || 0}`);
