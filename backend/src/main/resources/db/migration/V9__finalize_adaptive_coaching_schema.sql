alter table nutrition_day_reviews
    drop constraint nutrition_day_review_estimate;

update nutrition_day_reviews
set status = 'FASTING', estimated_total_kcal = null
where status = 'ESTIMATED_TOTAL' and estimated_total_kcal = 0;

alter table nutrition_day_reviews
    add constraint nutrition_day_review_estimate check (
        (status = 'ESTIMATED_TOTAL' and estimated_total_kcal is not null and estimated_total_kcal > 0)
        or (status <> 'ESTIMATED_TOTAL' and estimated_total_kcal is null)
    );

-- V7 temporarily created placeholder programs from the legacy goal settings. They
-- have no resolved nutrition values and must not bypass the new guided setup. The
-- original settings remain intact in user_goal_settings.
delete from nutrition_program_revisions
where source = 'LEGACY';

alter table nutrition_program_revisions
    drop column legacy_settings;
