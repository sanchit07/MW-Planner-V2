import MultiSelect, { TreeNode } from "@components/ui/MultiSelect";
import { useLazyFilterCompaniesQuery } from "@services/campaign/campaignSlice";
import { useTranslate } from "@tolgee/react";
import React, { useCallback, useRef, useState } from "react";

import type { Company } from "../../types/campaign.types";

const PAGE_SIZE = 50;

export interface MediaOwnerDropdownProps {
  value: string[];
  onChange: (selectedIds: string[]) => void;
  placeholder?: string; // defaults to translated "Select media owners"
  disabled?: boolean;
  companyType?: string;
  country?: string;
  id?: string;
  staticOptions?: TreeNode[];
}

const mapCompaniesToNodes = (companies: Company[]): TreeNode[] =>
  companies.map((c) => ({
    id: c.id,
    label: c.name,
    value: c.id,
  }));

export const MediaOwnerDropdown: React.FC<MediaOwnerDropdownProps> = ({
  value,
  onChange,
  placeholder,
  disabled = false,
  companyType = "MEDIA_OWNER",
  country,
  id = "media-owner-dropdown",
  staticOptions,
}) => {
  const { t: tCampaigns } = useTranslate(["campaigns"]);
  const [filterCompanies] = useLazyFilterCompaniesQuery();
  const [options, setOptions] = useState<TreeNode[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [offset, setOffset] = useState(0);
  const [searchTerm, setSearchTerm] = useState("");
  const searchRequestIdRef = useRef(0);
  const loadMoreInProgressRef = useRef(false);
  const filterCompaniesRef = useRef(filterCompanies);
  const companyTypeRef = useRef(companyType);
  const countryRef = useRef(country);
  filterCompaniesRef.current = filterCompanies;
  companyTypeRef.current = companyType;
  countryRef.current = country;

  const loadFirstPage = useCallback(async (term: string) => {
    const requestId = ++searchRequestIdRef.current;
    try {
      setIsLoading(true);
      const result = await filterCompaniesRef
        .current({
          offset: 0,
          limit: PAGE_SIZE,
          company_type: companyTypeRef.current,
          ...(term.trim() !== "" && { search: term.trim() }),
          ...(countryRef.current && { country: countryRef.current }),
        })
        .unwrap();

      if (requestId !== searchRequestIdRef.current) return;

      const list = Array.isArray(result?.data)
        ? result.data
        : ((result as { data?: { data?: Company[] } })?.data?.data ?? []);
      const total =
        (
          result as {
            meta?: { total: number };
            data?: { meta?: { total: number } };
          }
        )?.meta?.total ??
        (result as { data?: { meta?: { total: number } } })?.data?.meta
          ?.total ??
        0;
      setOptions(mapCompaniesToNodes(list));
      setOffset(list.length);
      setHasMore(list.length < total);
    } catch (error) {
      console.log("Error loading more media owners:", error);
      if (requestId === searchRequestIdRef.current) {
        setOptions([]);
        setHasMore(false);
      }
    } finally {
      if (requestId === searchRequestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  const onRemoteLoad = useCallback(() => {
    setSearchTerm("");
    loadFirstPage("");
  }, [loadFirstPage]);

  const onRemoteSearch = useCallback(
    (term: string) => {
      setSearchTerm(term);
      loadFirstPage(term);
    },
    [loadFirstPage],
  );

  const onRemoteLoadMore = useCallback(async () => {
    if (loadMoreInProgressRef.current || !hasMore) return;
    loadMoreInProgressRef.current = true;
    try {
      setIsLoadingMore(true);
      const result = await filterCompaniesRef
        .current({
          offset,
          limit: PAGE_SIZE,
          company_type: companyTypeRef.current,
          ...(searchTerm.trim() !== "" && { search: searchTerm.trim() }),
          ...(countryRef.current && { country: countryRef.current }),
        })
        .unwrap();

      const list = Array.isArray(result?.data)
        ? result.data
        : ((result as { data?: { data?: Company[] } })?.data?.data ?? []);
      const total =
        (result as { meta?: { total: number } })?.meta?.total ??
        (result as { data?: { meta?: { total: number } } })?.data?.meta
          ?.total ??
        0;
      const newOptions = mapCompaniesToNodes(list);
      const nextOffset = offset + list.length;
      setOptions((prev) => [...prev, ...newOptions]);
      setOffset(nextOffset);
      setHasMore(nextOffset < total);
    } catch (error) {
      console.log("Error loading more media owners:", error);
      setHasMore(false);
    } finally {
      setIsLoadingMore(false);
      loadMoreInProgressRef.current = false;
    }
  }, [offset, searchTerm, hasMore]);

  if (staticOptions) {
    return (
      <MultiSelect
        id={id}
        options={staticOptions}
        value={value}
        onChange={onChange}
        placeholder={
          placeholder ?? tCampaigns("mediaOwnerDropdown.placeholder")
        }
        searchable
        disabled={disabled}
      />
    );
  }

  return (
    <MultiSelect
      id={id}
      options={options}
      value={value}
      onChange={onChange}
      placeholder={
        isLoading
          ? tCampaigns("mediaOwnerDropdown.loading")
          : (placeholder ?? tCampaigns("mediaOwnerDropdown.placeholder"))
      }
      searchable
      disabled={disabled || isLoading}
      onRemoteLoad={onRemoteLoad}
      onRemoteSearch={onRemoteSearch}
      onRemoteLoadMore={
        hasMore && !isLoadingMore ? onRemoteLoadMore : undefined
      }
    />
  );
};

export default MediaOwnerDropdown;
