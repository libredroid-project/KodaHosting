-- Migration: Add latest_app_version and latest_tos_version to app_settings

INSERT INTO public.app_settings (key, value)
VALUES (
    'latest_app_version',
    '8500'::jsonb
)
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

INSERT INTO public.app_settings (key, value)
VALUES (
    'latest_tos_version',
    '1711200000'::jsonb
)
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
