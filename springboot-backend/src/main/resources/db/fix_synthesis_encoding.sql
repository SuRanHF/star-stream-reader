-- 修复早期导入时被错误转码的合成配方文本。
-- 只更新展示文本，不删除玩家数据，也不改变 recipe_key、材料、价格和效果。
SET NAMES utf8mb4;

UPDATE `synthesis_recipes`
SET `name` = CASE `recipe_key`
    WHEN 'craft_medium_hp' THEN '合成中瓶生命药剂'
    WHEN 'craft_large_hp' THEN '合成大瓶生命药剂'
    WHEN 'craft_stamina_pill' THEN '合成活力丹'
    WHEN 'craft_story_scroll' THEN '合成故事卷轴'
    WHEN 'craft_abyss_elixir' THEN '合成深渊秘药'
    WHEN 'craft_hunter_blade' THEN '合成猎人短刃'
    ELSE `name`
  END,
  `description` = CASE `recipe_key`
    WHEN 'craft_medium_hp' THEN '用2个小瓶生命药剂合成1个中瓶'
    WHEN 'craft_large_hp' THEN '用3个中瓶生命药剂合成1个大瓶'
    WHEN 'craft_stamina_pill' THEN '用1瓶圣水+1个魔物骨骸合成活力丹'
    WHEN 'craft_story_scroll' THEN '用5个故事碎片合成1个故事卷轴'
    WHEN 'craft_abyss_elixir' THEN '用深渊碎片+故事卷轴合成'
    WHEN 'craft_hunter_blade' THEN '用生锈短刀+猎人的戒指合成'
    ELSE `description`
  END
WHERE `recipe_key` IN (
  'craft_medium_hp',
  'craft_large_hp',
  'craft_stamina_pill',
  'craft_story_scroll',
  'craft_abyss_elixir',
  'craft_hunter_blade'
);
