package com.raresb.weaponskills;

import java.util.EnumMap;
import java.util.Map;

/** What each skill gains when it evolves (at skill level 10 by default). */
final class Evolutions {
    private static final Map<Skill, String> TEXT = new EnumMap<>(Skill.class);

    static {
        TEXT.put(Skill.WHIRLWIND, "Rotire mai largă (5 blocuri) și o a doua rotire după jumătate de secundă.");
        TEXT.put(Skill.GROUND_SLAM, "Undă de șoc mai mare, care și amețește.");
        TEXT.put(Skill.DASH_STRIKE, "Țâșnire mai lungă, care aruncă inamicii în aer.");
        TEXT.put(Skill.PARRY, "Gardă mai lungă, iar riposta lovește tot ce e în jurul tău.");
        TEXT.put(Skill.RISING_SLASH, "Aruncă mai sus inamicii din jurul tău.");
        TEXT.put(Skill.BLADE_FLURRY, "Tăieturile te înconjoară și lovesc și în lateral și în spate (rază mai mare).");
        TEXT.put(Skill.SHADOW_STEP, "O explozie de umbră lovește și orbește tot ce e lângă țintă.");
        TEXT.put(Skill.BLADE_DANCE, "Durează cu 50% mai mult și adaugă Forță.");
        TEXT.put(Skill.MIRROR_IMAGE, "Trei momeli, care explodează când dispar.");
        TEXT.put(Skill.JUDGMENT, "Mai multe lame pe o zonă mult mai largă.");
        TEXT.put(Skill.CRESCENT_WAVE, "Trei valuri în evantai.");
        TEXT.put(Skill.COUNTER_STANCE, "Primești cu 70% mai puține daune, iar contraatacul lovește de jur împrejur.");
        TEXT.put(Skill.THOUSAND_CUTS, "Douăzeci de tăieturi, iar ultima lovește triplu.");
        TEXT.put(Skill.CLEAVE, "O lovitură în cerc complet, care lovește de jur împrejur.");
        TEXT.put(Skill.EXECUTIONERS_DROP, "Impact mai mare și amețire mai lungă.");
        TEXT.put(Skill.BERSERKER_CHARGE, "Șarjă mai lungă și Forță II după aceea.");
        TEXT.put(Skill.AXE_THROW, "Arunci trei topoare în evantai.");
        TEXT.put(Skill.WAR_CRY, "Vindecă și jucătorii din apropiere și le dă Forță.");
        TEXT.put(Skill.EARTHSPLITTER, "Trei fisuri: una înainte și câte una în fiecare parte.");
        TEXT.put(Skill.BLOODLUST, "Uciderile vindecă dublu și dau un impuls de Viteză.");
        TEXT.put(Skill.GROUND_POUND, "Zonă mai mare și încetinire mai puternică.");
        TEXT.put(Skill.CHAIN_HOOK, "Încă două lanțuri agață cei mai apropiați inamici din fața ta.");
        TEXT.put(Skill.RAGNAROK, "Durează 15 secunde, cu o aură arzătoare în jurul tău.");
        TEXT.put(Skill.IMPALE, "Fandat de 9 blocuri, iar toate țintele sunt țintuite.");
        TEXT.put(Skill.DRAGOON_DIVE, "Impact mai larg, iar inamicii loviți sunt țintuiți.");
        TEXT.put(Skill.LANCE_CHARGE, "Șarjă mai lungă care nu se oprește: izbești tot ce e în cale.");
        TEXT.put(Skill.JAVELIN, "Străpunge cinci inamici, iar ultimul e țintuit 2 secunde.");
        TEXT.put(Skill.POLE_VAULT, "Sari mai departe și primești Viteză II 4 secunde.");
        TEXT.put(Skill.SWEEPING_ARC, "Rază de 6 blocuri, iar inamicii loviți sunt încetiniți.");
        TEXT.put(Skill.SPEAR_WALL, "Durează 6 secunde și înțeapă de jur împrejur, nu doar în față.");
        TEXT.put(Skill.THRUST_FLURRY, "Douăsprezece împunsături.");
        TEXT.put(Skill.PINNING_THROW, "Și inamicii din jurul țintei (3 blocuri) sunt țintuiți.");
        TEXT.put(Skill.DRAGON_LEAP, "Impact mai larg care și amețește inamicii.");
        TEXT.put(Skill.GUNGNIR, "Un al patrulea cerc de țepi, mai larg.");
        TEXT.put(Skill.TOMAHAWK_RAIN, "Optsprezece topoare pe o zonă mai largă.");
        TEXT.put(Skill.LUMBERJACKS_FURY, "Opt lovituri, pe o rază mai mare, care și împing inamicii.");
        TEXT.put(Skill.BONE_BREAKER, "Ținta e și slăbită și strălucește prin pereți.");
        TEXT.put(Skill.SHIELD_BASH, "Lovește un arc mult mai larg și împinge inamicii mai departe.");
        TEXT.put(Skill.FORTIFY, "Oferă și Absorbție.");
        TEXT.put(Skill.REFLECT, "Durează mai mult, iar proiectilele reflectate lovesc de două ori mai tare.");
        TEXT.put(Skill.GUARDIAN_AURA, "Rază de 10 blocuri și cu 40% mai puține daune.");
        TEXT.put(Skill.TAUNT, "Creaturile provocate sunt slăbite.");
        TEXT.put(Skill.SHIELD_THROW, "Sare între cinci inamici.");
        TEXT.put(Skill.PHALANX, "Rază de 10 blocuri, durează mai mult și dă Absorbție.");
        TEXT.put(Skill.ARROW_RAIN, "Aproape de două ori mai multe săgeți pe o zonă mai largă.");
        TEXT.put(Skill.PIERCING_SHOT, "Trage trei săgeți străpungătoare.");
        TEXT.put(Skill.VOLLEY, "Nouă săgeți în loc de cinci.");
        TEXT.put(Skill.WIND_ARROW, "Rafală mai mare și mai puternică; inamicii prinși în ea primesc și daune.");
        TEXT.put(Skill.HOVER_SHOT, "Șase săgeți și plutire mai lungă.");
        TEXT.put(Skill.RAIN_OF_FIRE, "Mai multe săgeți, iar pământul arde încă 3 secunde.");
        TEXT.put(Skill.TRAP_ARROW, "Capcană mai mare, care prinde toți inamicii din ea.");
        TEXT.put(Skill.SPLIT_SHOT, "Se desface în opt săgeți.");
        TEXT.put(Skill.SNIPER_MODE, "Daune x1.5 și străpunge cinci inamici.");
        TEXT.put(Skill.STORM_OF_ARROWS, "Durează 8 secunde, cu de două ori mai multe săgeți.");
        TEXT.put(Skill.EXPLOSIVE_BOLT, "Încă trei explozii în jurul primei.");
        TEXT.put(Skill.NET_SHOT, "O plasă mult mai mare.");
        TEXT.put(Skill.HARPOON_BOLT, "Trage și tot ce e lângă țintă.");
        TEXT.put(Skill.BURST_FIRE, "Cinci săgeți în loc de trei.");
        TEXT.put(Skill.RECOIL_SHOT, "Trage trei săgeți.");
        TEXT.put(Skill.GATLING, "Șaisprezece săgeți.");
        TEXT.put(Skill.SMOKE_BOLT, "Nor mai mare, care durează mai mult și slăbește inamicii.");
        TEXT.put(Skill.CHAIN_BOLT, "Leagă patru inamici, iar lanțurile îi electrocutează în fiecare secundă.");
        TEXT.put(Skill.ARTILLERY_BARRAGE, "Douăzeci de obuze pe o zonă mai largă.");
        TEXT.put(Skill.RIPTIDE_SURGE, "Avânt mai lung, care aruncă inamicii în aer.");
        TEXT.put(Skill.TIDAL_WAVE, "De două ori mai lat și ajunge mai departe.");
        TEXT.put(Skill.LIGHTNING_CALL, "Încă trei fulgere lovesc inamicii din apropiere.");
        TEXT.put(Skill.POSEIDONS_CALL, "Valul te poartă mai departe, apoi primești Grația delfinului și Respirație sub apă.");
        TEXT.put(Skill.HARPOON_THROW, "Străpunge și târăște până la trei inamici.");
        TEXT.put(Skill.TSUNAMI, "Cinci valuri mai late, care și slăbesc inamicii.");
        TEXT.put(Skill.GRAVITY_WELL, "Trage de la distanță mai mare, apoi strivește tot ce a fost tras.");
        TEXT.put(Skill.TREMOR, "Un al doilea cutremur o clipă mai târziu.");
        TEXT.put(Skill.METEOR, "Craterul mai arde încă 3 secunde.");
        TEXT.put(Skill.WIND_BURST, "Te aruncă mai sus și împinge mai tare, pe o rază mai mare.");
        TEXT.put(Skill.ANVIL_DROP, "Încă două nicovale cad în jurul țintei.");
        TEXT.put(Skill.CATACLYSM, "O a patra undă, iar undele trag inamicii spre centru.");
        TEXT.put(Skill.TREMOR_SENSE, "Rază mai mare și durează mai mult.");
        TEXT.put(Skill.TUNNEL_BORE, "Sapă cinci blocuri în adâncime în loc de trei.");
        TEXT.put(Skill.MINERS_RUSH, "Și mai rapid, și durează cu 50% mai mult.");
        TEXT.put(Skill.SHATTER, "O explozie mai mare.");
        TEXT.put(Skill.CLEAR_CUT, "O suprafață de 16x16.");
        TEXT.put(Skill.GROVE_GROWTH, "Rază de 12 blocuri.");
        TEXT.put(Skill.LUMBER_FRENZY, "Durează 30 de secunde și dă doi bușteni în plus.");
        TEXT.put(Skill.HARVEST_WAVE, "O zonă de 9x9.");
        TEXT.put(Skill.BURROW, "Durează mai mult și primești și Viteză II.");
        TEXT.put(Skill.GRAPPLE_HOOK, "Ajunge la 45 de blocuri, iar dacă agăți o creatură, o tragi pe ea spre tine.");
    }

    private Evolutions() {
    }

    static String of(Skill skill) {
        return WeaponSkillsPlugin.text("evolution." + skill.id(), TEXT.getOrDefault(skill, "Mai puternică din toate punctele de vedere."));
    }
}
