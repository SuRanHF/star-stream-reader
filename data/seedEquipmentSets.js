// 用户输入的文件 class 草稿分页 草稿页
// 装备套装种子数据 (Phase 3 - Round 2 Expansion)
// 当装备2+件同套装时，解锁额外属性加成

function seedEquipmentSets(db) {
  var insert = db.prepare(`
    INSERT OR IGNORE INTO equipment_sets (set_key, set_name, pieces_json, bonuses_json)
    VALUES (?, ?, ?, ?)
  `);

  var sets = [
    // ========================================== 车站幸存者套装 ==========================================
    {
      set_key: 'station_survivor',
      set_name: '车站幸存者套装',
      pieces: ['station_guard_coat', 'rusty_dagger', 'old_reader_badge'],
      bonuses: [
        { pieces_required: 2, bonus: { atk: 5, def: 3 } },
        { pieces_required: 3, bonus: { atk: 10, def: 8, hp: 50 } }
      ]
    },

    // ========================================== 剧本执行者套装 ==========================================
    {
      set_key: 'script_executor',
      set_name: '剧本执行者套装',
      pieces: ['script_execution_blade', 'observer_longcoat', 'channel_headset'],
      bonuses: [
        { pieces_required: 2, bonus: { atk: 8, spd: 3 } },
        { pieces_required: 3, bonus: { atk: 15, spd: 8, def: 5, critRate: 0.05 } }
      ]
    },

    // ========================================== 星辰观测者套装 ==========================================
    {
      set_key: 'star_observer',
      set_name: '星辰观测者套装',
      pieces: ['fallen_star_badge', 'worldline_compass', 'black_gloves'],
      bonuses: [
        { pieces_required: 2, bonus: { def: 5, spd: 5 } },
        { pieces_required: 3, bonus: { def: 12, spd: 10, hp: 60 } }
      ]
    },

    // ========================================== 王座之主套装 ==========================================
    {
      set_key: 'throne_master',
      set_name: '王座之主套装',
      pieces: ['nameless_crown', 'throne_fragment', 'final_chapter_page'],
      bonuses: [
        { pieces_required: 2, bonus: { atk: 12, def: 8 } },
        { pieces_required: 3, bonus: { atk: 25, def: 15, hp: 120, critDamage: 0.15 } }
      ]
    },

    // ========================================== 深渊漫步者套装 ==========================================
    {
      set_key: 'abyss_walker',
      set_name: '深渊漫步者套装',
      pieces: ['black_gloves', 'worldline_compass', 'rusty_dagger'],
      bonuses: [
        { pieces_required: 2, bonus: { def: 7, hp: 40 } },
        { pieces_required: 3, bonus: { def: 14, hp: 90, spd: 4 } }
      ]
    },

    // ========================================== 星流观察者套装 ==========================================
    {
      set_key: 'star_stream_observer',
      set_name: '星流观察者套装',
      pieces: ['fallen_star_badge', 'observer_longcoat', 'channel_headset'],
      bonuses: [
        { pieces_required: 2, bonus: { spd: 5, luck: 2 } },
        { pieces_required: 3, bonus: { spd: 10, luck: 5, dropRate: 0.05 } }
      ]
    },

    // ========================================== 叙事重构者套装 ==========================================
    {
      set_key: 'narrative_reconstructor',
      set_name: '叙事重构者套装',
      pieces: ['script_execution_blade', 'final_chapter_page', 'old_reader_badge'],
      bonuses: [
        { pieces_required: 2, bonus: { atk: 5, insight: 3 } },
        { pieces_required: 3, bonus: { atk: 12, insight: 6, hp: 80 } }
      ]
    },

    // ========================================== 天命追寻者套装 ==========================================
    {
      set_key: 'destiny_seeker',
      set_name: '天命追寻者套装',
      pieces: ['nameless_crown', 'throne_fragment', 'station_guard_coat'],
      bonuses: [
        { pieces_required: 2, bonus: { atk: 6, def: 4, spd: 3 } },
        { pieces_required: 3, bonus: { atk: 14, def: 10, spd: 7, hp: 100 } }
      ]
    }
  ];

  for (var i = 0; i < sets.length; i++) {
    var s = sets[i];
    insert.run(s.set_key, s.set_name, JSON.stringify(s.pieces), JSON.stringify(s.bonuses));
  }

  console.log('Equipment sets seed complete: ' + sets.length + ' sets.');
}

module.exports = { seedEquipmentSets };
