import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useEntityCacheStore = defineStore('entity-cache', () => {
  const schoolNames = ref<Record<string, string>>({})
  const clubNames = ref<Record<string, string>>({})

  function setSchoolName(id: string, name: string) {
    schoolNames.value[id] = name
  }
  function setClubName(id: string, name: string) {
    clubNames.value[id] = name
  }
  function getSchoolName(id: string): string | undefined {
    return schoolNames.value[id]
  }
  function getClubName(id: string): string | undefined {
    return clubNames.value[id]
  }

  return { schoolNames, clubNames, setSchoolName, setClubName, getSchoolName, getClubName }
})
