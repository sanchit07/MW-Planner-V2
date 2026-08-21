// IAM API brand entity
export interface IamBrand {
  id: string;
  name: string;
  code?: string;
  description?: string;
  industry?: string;
  logo_url?: string;
  is_active?: boolean;
  sort_order?: number;
  company_ids?: string[];
  iab_category_ids?: string[];
  // Expanded category objects returned by IAM /metadata/brands. Each entry
  // already carries the display `name` and IAB code (`unique_id`), so the UI
  // reads these directly instead of resolving `iab_category_ids` UUIDs.
  iab_categories?: IamIabCategory[];
  created_at?: string;
  updated_at?: string;
  created_by_company_id?: string;
}

// Alias keeps BrandState in brandSlice.ts compiling without changes
export type Brand = IamBrand;

// IAM POST /api/v1/metadata/brands request body
export interface IamBrandCreateRequest {
  name: string;
  code?: string;
  description?: string;
  industry?: string;
  logo_url?: string;
  is_active?: boolean;
  sort_order?: number;
  company_ids?: string[];
  iab_category_ids?: string[];
}

// Query params for GET /api/v1/metadata/brands and GET /companies/:id/brands
export interface IamBrandsQueryParams {
  search?: string;
  active_only?: boolean;
  iab_category?: string;
  include?: string;
  page?: number;
  limit?: number;
}

// IAB Category type — unchanged (used by form display)
export interface IabCategory {
  name: string;
  code: string;
}

// IAM IAB category entity — GET /api/v1/metadata/iab-categories.
// `id` is the UUID that POST /metadata/brands expects in `iab_category_ids`
// (each entry is parsed as a UUID server-side). `unique_id` is the human IAB
// code (e.g. "IAB4"); `name` is the display label.
export interface IamIabCategory {
  id: string;
  unique_id?: string;
  name: string;
  tier?: number;
  full_path?: string;
  is_active?: boolean;
  code?: string;
}

// IAB taxonomy version — GET /metadata/iab-taxonomy-versions
export interface IabTaxonomyVersion {
  id: string;
  version?: string;
  name?: string;
  description?: string;
  source_url?: string;
  is_active?: boolean;
  is_current?: boolean;
  category_count?: number;
  created_at?: string;
  updated_at?: string;
  deleted_at?: string;
  synced_at?: string;
}

// Node returned by GET /metadata/iab-taxonomy-versions/{id}/hierarchy.
// `children` may be nested node objects (tree shape) or string references
// depending on the API version — the component handles both.
export interface IabTaxonomyNode {
  id: string;
  name: string;
  unique_id?: string;
  tier?: number;
  full_path?: string;
  is_active?: boolean;
  children?: IabTaxonomyNode[] | string[];
}
