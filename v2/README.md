# MW Planner - Out-of-Home Campaign Management System

## Overview

MW Planner is a comprehensive Out-of-Home (OOH) advertising campaign management platform designed for digital and traditional outdoor advertising. It supports media agencies, media owners, advertisers, and resellers, offering tools for campaign planning, inventory management, proposal generation, and creative assignment. The platform aims to streamline the entire OOH advertising workflow.

## System Architecture

### Frontend
- **Framework**: React 19 with TypeScript
- **Styling**: Tailwind CSS
- **State Management**: Redux Tool Kit
- **Routing**: React router dom
- **Build Tool**: Vite

### Backend
- **Language**: JAVA Sprintboot 
- **Authentication**: session-based
- **API Design**: RESTful with middleware and error handling

### Data Storage

- **Primary Database**: PostgreSQL

### Core Features
- **Campaign Management**: Full lifecycle management with workflows, including optimized campaign listing and detailed campaign views with real-time data fetching.
- **Inventory Management**: Digital and traditional OOH inventory with geolocation.
- **Proposal Generation**: Automated, customizable proposals.
- **Creative Management**: Assignment and scheduling.
- **Campaign Planning Wizard**: Guided multi-step creation.
- **Interactive Map Views**: Mapbox-powered visualization with drawing tools.
- **Availability Timeline**: Scheduling system.
- **Approval Workflows**: Role-based negotiation and approval.
- **Statement Management**: Comprehensive system for managing statements, custom fees, campaign associations, and change history, including advanced splitting capabilities (Equal, Monthly, Weekly, Campaign-based, Custom splits) with hierarchical display.
- **Dashboards**: Role-specific dashboards (e.g., Media Owner, Agency/Advertiser) with customizable widgets for sales performance, inventory utilization, campaign overview, budget tracking, and operational alerts.
- **Performance Optimization**: Implemented React Query caching, memoization, and skeleton loading for improved user experience.

### UI/UX Decisions
- **Design**: Responsive, mobile-first approach with desktop optimization.
- **Accessibility**: Built-in screen reader support and keyboard navigation.
- **Interactive Maps**: Mapbox GL integration with custom drawing capabilities.

## External Dependencies

- **Core Frameworks**: React, React DOM, TypeScript, Tailwind CSS.
- **UI Components**: Radix UI, Lucide React, react-hook-form, Zod.
- **Mapping & Visualization**: Mapbox GL JS, Mapbox Draw, react-calendar-timeline.

