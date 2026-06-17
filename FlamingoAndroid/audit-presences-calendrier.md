# Audit - Gestion des présences et du calendrier mensuel

## 1) Schéma Firestore

La présence ne repose pas sur des champs dynamiques dans `workers`. Elle est stockée dans une collection top-level `attendance`.

Chaque document `attendance` contient au minimum : `workerId`, `date`, `status`, `time`, `timestamp`, `createdAt`, `updatedAt`.

Points clés :
- `date` est au format `yyyy-MM-dd`.
- `status` vaut `present`, `absent`, `half` ou `off`.
- Web écrit avec `create('attendance', ...)`, donc avec ID auto-généré.
- Android écrit avec un ID déterministe `"${workerId}_$date"`.

Références :
- [AttendanceRecord](OceanWeb/src/types/workers.ts#L30)
- [lecture/écriture attendance côté Web](OceanWeb/src/pages/Workers.tsx#L143)
- [AttendanceRecord Android](FlamingoAndroid/app/src/main/java/com/example/oceanandroid/data/models/Models.kt#L174)
- [upsertWorkerAttendance](FlamingoAndroid/app/src/main/java/com/example/oceanandroid/data/firebase/FirebaseService.kt#L183)

## 2) Calcul de présence et de salaire

La logique mensuelle côté Web est centralisée dans [generateMonthlySummary](OceanWeb/src/lib/salaryCalculations.ts#L39). Elle filtre les lignes `attendance` sur le mois courant puis calcule :

- `present` = 1 jour
- `half` = 0.5 jour
- `absent` et `off` = 0 jour pour le brut
- `totalEarned` = jours travaillés × salaire journalier
- `netSalary` = brut - avances - pénalités
- `remaining` = net - déjà payé

Le calcul net est borné à zéro via [calculateNetSalary](OceanWeb/src/lib/salaryCalculations.ts#L7).

Côté écran `Workers`, la page :
- lit `attendance` par `workerId` et `date`,
- prend le dernier record du jour,
- recalcule les compteurs du worker,
- met à jour le résumé global.

Références :
- [calculateNetSalary](OceanWeb/src/lib/salaryCalculations.ts#L7)
- [calculateRemainingSalary](OceanWeb/src/lib/salaryCalculations.ts#L11)
- [generateMonthlySummary](OceanWeb/src/lib/salaryCalculations.ts#L39)
- [toggleDayStatus](OceanWeb/src/pages/Workers.tsx#L410)
- [recalculateWorkerStats](OceanWeb/src/pages/Workers.tsx#L380)
- [getNetSalary](OceanWeb/src/pages/Workers.tsx#L552)

## 3) Calendrier mensuel horizontal

Le calendrier horizontal est généré uniquement côté Web dans [Workers.tsx](OceanWeb/src/pages/Workers.tsx). Le mois est construit avec `eachDayOfInterval({ start: firstDay, end: lastDay })`, puis rendu par `daysArray.map(...)`.

Chaque colonne représente un jour du mois et affiche une pastille colorée :

- `present` -> vert (`bg-green-500`)
- `half` -> orange (`bg-orange-500`)
- `absent` -> rouge (`bg-red-500`)
- `off` -> gris (`bg-slate-400`)
- avant l'entrée du worker -> rouge pâle (`bg-red-500/40`)
- aucun record -> gris clair (`bg-slate-200`)

Références :
- [construction du mois](OceanWeb/src/pages/Workers.tsx#L150)
- [rendu du calendrier](OceanWeb/src/pages/Workers.tsx#L757)
- [mapping des couleurs](OceanWeb/src/pages/Workers.tsx#L797)
- [bindPresenceStatus Android](FlamingoAndroid/app/src/main/java/com/example/oceanandroid/presentation/util/AdminUiUtils.kt#L41)

## Conclusion

Il n'existe pas de calendrier mensuel stocké dans Firestore. La vue est reconstruite à partir des documents `attendance`, filtrés par travailleur et par date, puis affichés dans une grille horizontale. Le salaire net affiché correspond au brut moins avances et pénalités, avec un restant à payer calculé à part.


