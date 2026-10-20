# Test tags

Format `<app>_<screen>_<element>`; `hm` = House Manual, `fl` = Field Log. Both app roots set `testTagsAsResourceId`, so Maestro targets these as ids.

| Tag | Where |
| --- | --- |
| `<app>_tab_<id>` | Bottom bar / rail item (`home`, `house`, `items`, `reminders`, `more`; `today`, `jobs`, `clock`, `schedule`, `more`) |
| `<app>_back` | Back arrow in `ScreenScaffold` on every non-root screen |
| `row_<id>` | List rows: floors (`row_<floorId>`), Team members (`row_<userId>`) |
| `hm_onboarding_{name,address,type,foundation_<basement\|walkout_basement\|crawlspace\|slab>,garage,draw,skip}` | HM-Onboarding fields and buttons |
| `hm_floors_fab`, `hm_floors_menu_<floorId>` | HM-Floors add button, row menu |
| `hm_house_floor_<floorId>`, `hm_house_edit_plan`, `hm_house_floors` | Blueprint floor chips and buttons |
| `<app>_layer_<wire>` | Blueprint layer chips (`hm_layer_walls`...) |
| `hm_plan_{fit,undo,redo,add_room,canvas,room_name,room_kind,room_width,room_length,done,edit_shape,add_stairs,delete_room,add_door,add_window,opening_width,delete_opening}`, `hm_plan_floor_<floorId>` | Plan editor |
| `<app>_sign_in_{email,send,google,code,verify,change_email}` | Shared sign-in (`fl_sign_in_email`) |
| `fl_welcome_{name,trade,phone,code,submit,sign_out}` | FL-Welcome |
| `fl_team_{share,new_code,remove_confirm}` | FL-Team |
| `fl_company_{name,trade,phone,email,address,distance,ot_<rule>}` | FL-Company |
| `fl_more_{team,company,sign_out,sign_out_confirm}` | FL More tab |
