create extension if not exists pg_trgm;

-- Keep Norwegian letters, and turn punctuation into word boundaries. No stemming
-- or stop-word removal: e.g. "without salt" must retain both words.
create function search_normalize(value text) returns text
language sql immutable parallel safe as $$
    select trim(regexp_replace(lower(coalesce(value, '')), '[^[:alnum:]]+', ' ', 'g'))
$$;

create function search_matches(document text, query text, stage text) returns boolean
language sql immutable parallel safe as $$
    select query <> '' and not exists (
        select 1 from unnest(string_to_array(query, ' ')) as terms(term)
        where not (
            (' ' || document || ' ') like ('% ' || term || ' %')
            or (stage = 'PREFIX' and length(term) >= 2
                and (' ' || document) like ('% ' || term || '%'))
            or (stage = 'TYPO' and length(term) >= 4
                and strict_word_similarity(term, document) > 0.4)
        )
    )
$$;

create function search_rank(label text, brand text, query text, stage text) returns integer
language sql immutable parallel safe as $$
    select case
        when search_normalize(label) = query then 0
        when search_normalize(split_part(label, ',', 1)) = query then 1
        when (search_normalize(label) || ' ') like (query || ' %') then 2
        when stage = 'PREFIX' and search_normalize(label) like (query || '%') then 2
        when search_matches(search_normalize(label), query, stage) then 3
        when search_matches(search_normalize(brand), query, stage) then 5
        else 4
    end
$$;

create function search_similarity(document text, query text) returns double precision
language sql immutable parallel safe as $$
    select coalesce(min(strict_word_similarity(term, document)), 0)::double precision
    from unnest(string_to_array(query, ' ')) as terms(term)
$$;

create index food_revisions_search_idx on food_revisions
    using gin (search_normalize(name || ' ' || coalesce(brand, '')) gin_trgm_ops);
create index food_revisions_brand_search_idx on food_revisions
    using gin (search_normalize(brand) gin_trgm_ops);
create index food_aliases_search_idx on food_aliases
    using gin (search_normalize(name) gin_trgm_ops);
create index recipe_revisions_search_idx on recipe_revisions
    using gin (search_normalize(name) gin_trgm_ops);
