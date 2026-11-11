ALTER TABLE group_members
    ADD CONSTRAINT ck_group_members_role
        CHECK (role IN ('OWNER', 'MEMBER'));

ALTER TABLE expenses
    ADD CONSTRAINT ck_expenses_category
        CHECK (category IN (
                            'FOOD',
                            'HOME_AND_BILLS',
                            'TRANSPORT',
                            'HEALTHCARE_AND_HYGIENE',
                            'CLOTHING_AND_FOOTWEAR',
                            'ENTERTAINMENT_AND_RELAXATION',
                            'SAVINGS_AND_INVESTMENTS',
                            'OTHER'
            ));
