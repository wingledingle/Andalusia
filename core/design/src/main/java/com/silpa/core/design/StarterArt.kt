package com.silpa.core.design

/** Bundled CC0 / public-domain artwork used across the UI and as the Image Bank starter pack. See ART_CREDITS.md. */
data class StarterArt(val key: String, val res: Int, val category: String)

val StarterArtwork: List<StarterArt> = listOf(
    StarterArt("yukata_girl", R.drawable.art_yukata_girl, "Characters"),
    StarterArt("pointing_schoolgirl", R.drawable.art_pointing_schoolgirl, "Characters"),
    StarterArt("kabuki_print", R.drawable.art_kabuki_print, "Characters"),
    StarterArt("fashion_girl", R.drawable.art_fashion_girl, "Characters"),
    StarterArt("anime_eyes", R.drawable.art_anime_eyes, "Eyes"),
    StarterArt("umbrella_girl", R.drawable.art_umbrella_girl, "Characters"),
    StarterArt("basket_girl", R.drawable.art_basket_girl, "Poses"),
    StarterArt("snorkel_boy", R.drawable.art_snorkel_boy, "Poses"),
    StarterArt("witch_broom", R.drawable.art_witch_broom, "Characters"),
    StarterArt("angry_girl", R.drawable.art_angry_girl, "Faces"),
    StarterArt("cat_girl", R.drawable.art_cat_girl, "Characters"),
    StarterArt("scarf_girl", R.drawable.art_scarf_girl, "Poses"),
    StarterArt("apple_girl", R.drawable.art_apple_girl, "Characters"),
    StarterArt("net_boy", R.drawable.art_net_boy, "Poses"),
    StarterArt("bug_boy", R.drawable.art_bug_boy, "Characters"),
    StarterArt("student_boy", R.drawable.art_student_boy, "Faces"),
    StarterArt("autumn_boy", R.drawable.art_autumn_boy, "Poses"),
    StarterArt("leaves_boy", R.drawable.art_leaves_boy, "Poses"),
    StarterArt("peace_girl", R.drawable.art_peace_girl, "Faces"),
    StarterArt("office_girl", R.drawable.art_office_girl, "Characters"),
    StarterArt("food_girl", R.drawable.art_food_girl, "Faces"),
    StarterArt("blond_boy", R.drawable.art_blond_boy, "Faces"),
    StarterArt("kindergarten", R.drawable.art_kindergarten, "Characters"),
    StarterArt("couple_chat", R.drawable.art_couple_chat, "Characters"),
    StarterArt("hot_spring", R.drawable.art_hot_spring, "Characters"),
    StarterArt("balloon_girl", R.drawable.art_balloon_girl, "Characters"),
    StarterArt("icecream_girl", R.drawable.art_icecream_girl, "Faces"),
    StarterArt("phone_girl", R.drawable.art_phone_girl, "Faces"),
)

/** Art used for the Home hero carousel, in display order. */
val HeroArt: List<StarterArt> = listOf("cat_girl", "pointing_schoolgirl", "yukata_girl", "witch_broom", "umbrella_girl", "scarf_girl", "kabuki_print")
    .mapNotNull { k -> StarterArtwork.firstOrNull { it.key == k } }
