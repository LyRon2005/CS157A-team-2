// ERD rating labels shared by the public form and My reviews.
(() => {
const common={cleanliness_rating:'Cleanliness',wifi_rating:'Wi-Fi',ac_rating:'Air conditioning'};
window.ReviewCriteria={building:{...common,elevator_rating:'Elevators',bathroom_rating:'Bathrooms',lounging_area_rating:'Lounge areas',study_spot_rating:'Study spots'},
    classroom:{...common,desk_space_rating:'Desk space',seating_comfort_rating:'Seating comfort',spaciousness_rating:'Space',lighting_rating:'Lighting',technology_rating:'Technology'}};
})();
