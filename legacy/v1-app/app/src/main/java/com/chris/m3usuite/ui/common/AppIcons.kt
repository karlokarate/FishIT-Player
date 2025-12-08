package com.chris.m3usuite.ui.common

import androidx.annotation.DrawableRes
import com.chris.m3usuite.R

enum class IconVariant { Primary, Duotone, Solid }

enum class AppIcon {
    Refresh,
    Profile,
    Settings,
    LiveTv,
    MovieVod,
    Series,
    All,
    PlayCircle,
    Restart,
    Play,
    Info,
    AddKid,
    RemoveKid,
    BookmarkAdd,
    BookmarkRemove,
    Search,
    AddProfile,
    Edit,
    Lock,
    Kids,
    Camera,
    Gallery,
}

@DrawableRes
fun AppIcon.resId(variant: IconVariant = IconVariant.Primary): Int =
    when (this) {
        AppIcon.Refresh -> if (variant == IconVariant.Duotone) R.drawable.ic_refresh_duotone else R.drawable.ic_refresh_primary
        AppIcon.Profile -> if (variant == IconVariant.Duotone) R.drawable.ic_profile_duotone else R.drawable.ic_profile_primary
        AppIcon.Settings -> if (variant == IconVariant.Duotone) R.drawable.ic_settings_duotone else R.drawable.ic_settings_primary
        AppIcon.LiveTv -> if (variant == IconVariant.Duotone) R.drawable.ic_live_tv_duotone else R.drawable.ic_live_tv_primary
        AppIcon.MovieVod -> if (variant == IconVariant.Duotone) R.drawable.ic_movie_vod_duotone else R.drawable.ic_movie_vod_primary
        AppIcon.Series -> if (variant == IconVariant.Duotone) R.drawable.ic_series_duotone else R.drawable.ic_series_primary
        AppIcon.All -> if (variant == IconVariant.Duotone) R.drawable.ic_all_duotone else R.drawable.ic_all_primary
        AppIcon.PlayCircle -> if (variant == IconVariant.Duotone) R.drawable.ic_play_circle_duotone else R.drawable.ic_play_circle_primary
        AppIcon.Restart -> if (variant == IconVariant.Duotone) R.drawable.ic_restart_duotone else R.drawable.ic_restart_primary
        AppIcon.Play -> if (variant == IconVariant.Duotone) R.drawable.ic_play_duotone else R.drawable.ic_play_primary
        AppIcon.Info -> if (variant == IconVariant.Duotone) R.drawable.ic_info_duotone else R.drawable.ic_info_primary
        AppIcon.AddKid -> R.drawable.ic_add_kid_solid
        AppIcon.RemoveKid -> R.drawable.ic_remove_kid_solid
        AppIcon.BookmarkAdd -> R.drawable.ic_bookmark_add_solid
        AppIcon.BookmarkRemove -> R.drawable.ic_bookmark_remove_solid
        AppIcon.Search -> if (variant == IconVariant.Duotone) R.drawable.ic_search_duotone else R.drawable.ic_search_primary
        AppIcon.AddProfile -> R.drawable.ic_add_profile_solid
        AppIcon.Edit -> if (variant == IconVariant.Duotone) R.drawable.ic_edit_duotone else R.drawable.ic_edit_primary
        AppIcon.Lock -> if (variant == IconVariant.Duotone) R.drawable.ic_lock_duotone else R.drawable.ic_lock_primary
        AppIcon.Kids -> if (variant == IconVariant.Duotone) R.drawable.ic_kids_duotone else R.drawable.ic_kids_primary
        AppIcon.Camera -> if (variant == IconVariant.Duotone) R.drawable.ic_camera_duotone else R.drawable.ic_camera_primary
        AppIcon.Gallery -> if (variant == IconVariant.Duotone) R.drawable.ic_gallery_duotone else R.drawable.ic_gallery_primary
    }
